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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

/**
 * Verifies cloud-stats provisioning is recorded even when a downstream {@link PlannedNodeBuilderFactory}
 * replaces the default {@link PlannedNodeBuilder} with one that returns an untracked
 * {@link NodeProvisioner.PlannedNode} (i.e. not a cloud-stats {@code TrackedItem}). That path is what
 * the cluster-gated {@code KubernetesCloudStatsLifecycleTest} cannot exercise, so it is reproduced here
 * against the public cloud-stats seams with no cluster.
 */
@WithJenkins
class KubernetesProvisioningActivityListenerTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    /** Registers a cloud named "Cloud" carrying one template that matches label "foo", and returns it. */
    private KubernetesCloud registerCloud() {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        template.setLabel("foo");
        cloud.addTemplate(template);
        r.jenkins.clouds.add(cloud);
        return cloud;
    }

    /**
     * Provisions one agent from a matching template and returns the (already-resolved) agent. Unlike a
     * real round, cloud-stats' {@code onStarted} is deliberately <em>not</em> fired, so no activity yet
     * exists — mirroring a downstream builder whose untracked planned node cloud-stats ignored.
     */
    private KubernetesSlave provisionAgent() throws Exception {
        Collection<NodeProvisioner.PlannedNode> planned =
                registerCloud().provision(new Cloud.CloudState(Label.get("foo"), 0), 1);
        return (KubernetesSlave) planned.iterator().next().future.get();
    }

    /** A plain planned node, as a downstream builder (e.g. CloudBees) would return: not a TrackedItem. */
    private static NodeProvisioner.PlannedNode untrackedPlannedNode(KubernetesSlave agent) {
        return new NodeProvisioner.PlannedNode(agent.getNodeName(), CompletableFuture.<Node>completedFuture(agent), 1);
    }

    private static long activityCountFor(ProvisioningActivity.Id id) {
        return CloudStatistics.get().getActivities().stream()
                .filter(a -> a.getId().getFingerprint() == id.getFingerprint())
                .count();
    }

    private static KubernetesProvisioningActivityListener listener() {
        return ExtensionList.lookupSingleton(KubernetesProvisioningActivityListener.class);
    }

    @Test
    void opensProvisioningActivityForUntrackedPlannedNode() throws Exception {
        KubernetesSlave agent = provisionAgent();
        ProvisioningActivity.Id id = agent.getId();
        assertNull(
                CloudStatistics.get().getPotentiallyCompletedActivityFor(id),
                "precondition: no activity until the listener opens one");

        listener().onComplete(untrackedPlannedNode(agent), agent);

        ProvisioningActivity activity = CloudStatistics.get().getPotentiallyCompletedActivityFor(id);
        assertNotNull(activity, "the listener should open a PROVISIONING activity for an untracked planned node");
        assertEquals(id, activity.getId());
        assertEquals(
                ProvisioningActivity.Phase.PROVISIONING,
                activity.getCurrentPhase(),
                "the opened activity should start in PROVISIONING so cloud-stats can advance it");
        assertEquals(1, activityCountFor(id), "exactly one activity for the attempt");
    }

    @Test
    void isIdempotentForUntrackedPlannedNode() throws Exception {
        KubernetesSlave agent = provisionAgent();
        ProvisioningActivity.Id id = agent.getId();

        listener().onComplete(untrackedPlannedNode(agent), agent);
        listener().onComplete(untrackedPlannedNode(agent), agent);

        assertEquals(1, activityCountFor(id), "a second completion must not open a duplicate activity");
    }

    @Test
    void doesNothingWhenPlannedNodeAlreadyTracked() throws Exception {
        KubernetesCloud cloud = registerCloud();
        Label label = Label.get("foo");
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new Cloud.CloudState(label, 0), 1);
        NodeProvisioner.PlannedNode trackedPlannedNode = planned.iterator().next();
        KubernetesSlave agent = (KubernetesSlave) trackedPlannedNode.future.get();
        ProvisioningActivity.Id id = agent.getId();

        // Stock-Jenkins path: the planned node is a TrackedPlannedNode and cloud-stats opens the
        // activity itself. The listener must not open a second one.
        assertEquals(TrackedPlannedNode.class, trackedPlannedNode.getClass());
        for (CloudProvisioningListener cpl : CloudProvisioningListener.all()) {
            cpl.onStarted(cloud, label, planned);
        }
        assertEquals(1, activityCountFor(id), "precondition: cloud-stats opened exactly one activity");

        listener().onComplete(trackedPlannedNode, agent);

        assertEquals(1, activityCountFor(id), "a tracked planned node must be left to cloud-stats, with no duplicate");
    }

    @Test
    void ignoresNonKubernetesNode() {
        // Registered globally, so it must be a safe no-op for other clouds' agents.
        Node other = Mockito.mock(Node.class);
        NodeProvisioner.PlannedNode plannedNode =
                new NodeProvisioner.PlannedNode("other", CompletableFuture.completedFuture(other), 1);
        assertDoesNotThrow(() -> listener().onComplete(plannedNode, other));
        assertEquals(
                0, CloudStatistics.get().getActivities().size(), "no activity should be opened for a non-k8s node");
    }
}
