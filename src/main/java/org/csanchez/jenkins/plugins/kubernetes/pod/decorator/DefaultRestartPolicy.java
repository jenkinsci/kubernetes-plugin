package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.fabric8.kubernetes.api.model.Pod;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

/**
 * Sets the restart policy to Never.
 */
@Extension
public class DefaultRestartPolicy implements PodDecorator {
    @NonNull
    @Override
    public Pod decorate(@NonNull KubernetesCloud kubernetesCloud, @NonNull Pod pod) {
        String restartPolicy = pod.getSpec().getRestartPolicy();
        if (restartPolicy == null || restartPolicy.isBlank()) {
            pod.getSpec().setRestartPolicy("Never");
        }
        return pod;
    }
}
