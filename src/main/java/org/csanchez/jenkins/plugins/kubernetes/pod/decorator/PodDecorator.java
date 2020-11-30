package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.fabric8.kubernetes.api.model.Pod;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

import javax.annotation.Nonnull;

/**
 * Allows to alter a pod definition after it has been built from the yaml and DSL/GUI configuration.
 */
public interface PodDecorator extends ExtensionPoint {

    @Nonnull
    static Pod decorateAll(@Nonnull KubernetesCloud kubernetesCloud, @Nonnull Pod pod) {
        for (PodDecorator decorator : ExtensionList.lookup(PodDecorator.class)) {
            pod = decorator.decorate(kubernetesCloud, pod);
        }
        return pod;
    }

    @Nonnull
    Pod decorate(@Nonnull KubernetesCloud kubernetesCloud, @Nonnull Pod pod);
}
