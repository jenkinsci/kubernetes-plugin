package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.ComputerListener;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesLauncher;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.PhaseExecutionAttachment;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.variant.OptionalExtension;

/**
 * Records a cloud-stats {@code LAUNCHING:FAIL} when a Kubernetes agent fails to launch. Registered only
 * when the cloud-stats plugin is installed (JENKINS-67256).
 *
 * <p>Cloud-stats ships an {@code OperationListener} that enters the {@code LAUNCHING} phase in
 * {@code preLaunch} and marks {@code OPERATING} in {@code onOnline}, but its {@code onLaunchFailure}
 * is a documented no-op. Without this listener a failed launch would leave the provisioning activity
 * in {@code LAUNCHING} with an {@code OK} status until {@code SlaveCompletionDetector} drives it to
 * {@code COMPLETED} on pod removal, so the failure would never surface in cloud-stats. This listener
 * fills that gap by attaching a {@code FAIL} with the failure reason to the {@code LAUNCHING} phase.
 *
 * <p>The tracking {@link ProvisioningActivity.Id} is obtained from the {@link TrackedKubernetesComputer}
 * (which resolves it by node name from the recorded activities), rather than from the agent, so this
 * stays a no-op for a plain (untracked) computer or a non-Kubernetes one.
 */
@OptionalExtension(requirePlugins = "cloud-stats")
public class KubernetesLaunchFailureListener extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(KubernetesLaunchFailureListener.class.getName());

    @Override
    public void onLaunchFailure(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
        if (!(c instanceof TrackedKubernetesComputer)) {
            return;
        }
        TrackedKubernetesComputer computer = (TrackedKubernetesComputer) c;
        KubernetesSlave node = computer.getNode();
        if (node == null) {
            return;
        }
        ProvisioningActivity.Id id = computer.getId();
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
