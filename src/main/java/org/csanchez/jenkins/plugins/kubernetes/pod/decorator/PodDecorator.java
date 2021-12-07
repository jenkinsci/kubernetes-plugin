package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.fabric8.kubernetes.api.model.Pod;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

/**
 * Allows to alter a pod definition after it has been built from the yaml and DSL/GUI configuration.
 */
public interface PodDecorator extends ExtensionPoint {

    @NonNull
    static Pod decorateAll(@NonNull KubernetesCloud kubernetesCloud, @NonNull Pod pod) {
        for (PodDecorator decorator : ExtensionList.lookup(PodDecorator.class)) {
            pod = decorator.decorate(kubernetesCloud, pod);
        }
        return pod;
    }

    @NonNull
    Pod decorate(@NonNull KubernetesCloud kubernetesCloud, @NonNull Pod pod);
}
