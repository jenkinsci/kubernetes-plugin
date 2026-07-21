/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;

/**
 * Ensures a cloud-stats {@code PROVISIONING} activity is opened for a Kubernetes agent whose planned
 * node was not cloud-stats-tracked, so provisioning is recorded no matter which
 * {@link PlannedNodeBuilder} produced the node.
 *
 * <p>The default {@link StandardPlannedNodeBuilder} returns a {@code TrackedPlannedNode}, so cloud-stats
 * opens the activity itself from its {@code ProvisioningListener.onStarted} and this listener is a
 * no-op. But {@link PlannedNodeBuilderFactory} is an extension point: another plugin — or a Jenkins
 * distribution — can register a factory whose builder returns a plain {@link NodeProvisioner.PlannedNode}
 * that is not a cloud-stats {@link TrackedItem}. cloud-stats then never opens a {@code PROVISIONING}
 * activity, so when the agent connects its {@code OperationListener} finds nothing to advance and logs
 * {@code "No activity tracked for ..."}, and the attempt is invisible in Cloud Statistics.
 *
 * <p>Wrapping the planned node up front is not always possible: a custom builder may create the node
 * asynchronously, so the tracking {@link ProvisioningActivity.Id} — which must equal the one the agent
 * later reports — is not known until the node resolves. The provisioned {@link KubernetesSlave} does
 * carry that Id (minted in its constructor), so once provisioning completes this listener opens the
 * activity through cloud-stats' public {@code ProvisioningListener#onStarted(Id)} seam, the one
 * documented for provisioning that happens outside the tracked-planned-node path. cloud-stats then
 * advances {@code LAUNCHING}/{@code OPERATING}/{@code COMPLETED} as usual, and
 * {@link KubernetesLaunchFailureListener} can record a {@code LAUNCHING:FAIL} against it.
 *
 * <p>Scope: this compensates only for a <em>successful</em> provisioning whose planned node was
 * untracked. A pure provisioning <em>failure</em> on that path produces no {@link Node}, hence no
 * {@link ProvisioningActivity.Id} to key an activity on, so it cannot be recorded here — unlike the
 * default path, where cloud-stats' own {@code ProvisioningListener.onFailure} records
 * {@code PROVISIONING:FAIL}. And because the activity is opened at {@code onComplete} (node already
 * built), its {@code PROVISIONING} phase carries no meaningful duration on this path; the value is
 * that the attempt becomes visible in Cloud Statistics and traverses the remaining phases.
 */
@Extension
public class KubernetesProvisioningActivityListener extends CloudProvisioningListener {

    private static final Logger LOGGER = Logger.getLogger(KubernetesProvisioningActivityListener.class.getName());

    @Override
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        if (!(node instanceof KubernetesSlave)) {
            return;
        }
        // A TrackedPlannedNode means cloud-stats already opened the activity from onStarted; opening it
        // again here would create a duplicate. This is the default-builder path, where nothing is needed.
        if (plannedNode instanceof TrackedItem) {
            return;
        }
        ProvisioningActivity.Id id = ((KubernetesSlave) node).getId();
        if (id == null) {
            return;
        }
        CloudStatistics statistics = CloudStatistics.get();
        // getPotentiallyCompletedActivityFor is the silent lookup; getActivityFor would log a spurious
        // "No activity tracked" warning on the expected miss. Guards against double-opening as well.
        if (statistics.getPotentiallyCompletedActivityFor(id) != null) {
            return;
        }
        CloudStatistics.ProvisioningListener.get().onStarted(id);
        LOGGER.log(
                Level.FINE,
                () -> "Opened cloud-stats provisioning activity for Kubernetes agent " + id.getNodeName()
                        + "; its planned node (" + plannedNode.getClass().getName() + ") was not cloud-stats-tracked");
    }
}
