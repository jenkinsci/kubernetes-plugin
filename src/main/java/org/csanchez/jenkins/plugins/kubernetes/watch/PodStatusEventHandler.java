package org.csanchez.jenkins.plugins.kubernetes.watch;

import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import java.util.Optional;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;

/**
 * Process pod events and print relevant information in build logs.
 * Registered as an informer in {@link org.csanchez.jenkins.plugins.kubernetes.KubernetesLauncher#launch(SlaveComputer, TaskListener)}).
 */
public class PodStatusEventHandler implements ResourceEventHandler<Pod> {

    private static final Logger LOGGER = Logger.getLogger(PodStatusEventHandler.class.getName());

    @Override
    public void onUpdate(Pod unused, Pod pod) {
        Optional<Node> found = Jenkins.get().getNodes().stream()
                .filter(n -> n.getNodeName().equals(pod.getMetadata().getName()))
                .findFirst();
        if (found.isPresent()) {
            final StringBuilder sb = new StringBuilder();
            pod.getStatus().getContainerStatuses().forEach(s -> sb.append(formatContainerStatus(s)));
            pod.getStatus()
                    .getConditions()
                    .forEach(c -> sb.append(formatPodStatus(c, pod.getStatus().getPhase())));
            if (!sb.toString().isEmpty()) {
                ((KubernetesSlave) found.get())
                        .getRunListener()
                        .getLogger()
                        .println("[PodInfo] " + pod.getMetadata().getName() + sb);
            }
        } else {
            LOGGER.fine(() -> "Event received for non-existent node: ["
                    + pod.getMetadata().getName() + "]");
        }
    }

    private String formatPodStatus(PodCondition c, String phase) {
        if (c.getReason() == null) {
            // not interesting
            return "";
        }
        return String.format("%n\tPod [%s][%s] %s", phase, c.getReason(), c.getMessage());
    }

    private String formatContainerStatus(ContainerStatus s) {
        ContainerState state = s.getState();
        if (state.getRunning() != null) {
            // don't care about running
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n\tContainer [%s]", s.getName()));
        if (state.getTerminated() != null) {
            String message = state.getTerminated().getMessage();
            sb.append(String.format(
                    " terminated [%s] %s",
                    state.getTerminated().getReason(), message != null ? message : "No message"));
        }
        if (state.getWaiting() != null) {
            String message = state.getWaiting().getMessage();
            sb.append(String.format(
                    " waiting [%s] %s", state.getWaiting().getReason(), message != null ? message : "No message"));
        }
        return sb.toString();
    }

    @Override
    public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
        // no-op
    }

    @Override
    public void onAdd(Pod pod) {
        // no-op
    }
}
