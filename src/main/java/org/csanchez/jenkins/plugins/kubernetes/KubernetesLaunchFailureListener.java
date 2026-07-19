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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.PhaseExecutionAttachment;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;

/**
 * Records a cloud-stats {@code LAUNCHING:FAIL} when a Kubernetes agent fails to launch.
 *
 * <p>Cloud-stats ships an {@code OperationListener} that enters the {@code LAUNCHING} phase in
 * {@code preLaunch} and marks {@code OPERATING} in {@code onOnline}, but its {@code onLaunchFailure}
 * is a documented no-op. Without this listener a failed launch would leave the provisioning activity
 * in {@code LAUNCHING} with an {@code OK} status until {@code SlaveCompletionDetector} drives it to
 * {@code COMPLETED} on pod removal, so the failure would never surface in cloud-stats. This listener
 * fills that gap by attaching a {@code FAIL} with the failure reason to the {@code LAUNCHING} phase.
 */
@Extension
public class KubernetesLaunchFailureListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(KubernetesLaunchFailureListener.class.getName());

    @Override
    public void onLaunchFailure(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
        if (!(c instanceof KubernetesComputer)) {
            return;
        }
        KubernetesSlave node = ((KubernetesComputer) c).getNode();
        if (node == null) {
            return;
        }
        ProvisioningActivity.Id id = node.getId();
        if (id == null) {
            return;
        }
        CloudStatistics statistics = CloudStatistics.get();
        ProvisioningActivity activity = statistics.getActivityFor(id);
        if (activity == null) {
            return;
        }
        // Cloud-stats' own OperationListener enters LAUNCHING in preLaunch; guard in case the launch
        // failed before that ran so the FAIL is never recorded against an earlier phase.
        activity.enterIfNotAlready(ProvisioningActivity.Phase.LAUNCHING);
        String reason = reasonFor(node);
        statistics.attach(
                activity,
                ProvisioningActivity.Phase.LAUNCHING,
                new PhaseExecutionAttachment(ProvisioningActivity.Status.FAIL, reason));
        LOGGER.log(
                Level.FINE, () -> "Recorded launch failure for Kubernetes agent " + id.getNodeName() + ": " + reason);
    }

    /**
     * The launcher records the underlying cause in {@link KubernetesLauncher#getProblem()} before the
     * launch is reported as failed, so it is the authoritative source for the failure reason. Core's
     * {@link ComputerListener#onLaunchFailure} carries no {@link Throwable} of its own.
     */
    private static String reasonFor(@NonNull KubernetesSlave node) {
        ComputerLauncher launcher = node.getLauncher();
        if (launcher instanceof KubernetesLauncher) {
            Throwable problem = ((KubernetesLauncher) launcher).getProblem();
            if (problem != null) {
                String message = problem.getMessage();
                return message != null && !message.isBlank() ? message : problem.toString();
            }
        }
        return "Launch failed";
    }
}
