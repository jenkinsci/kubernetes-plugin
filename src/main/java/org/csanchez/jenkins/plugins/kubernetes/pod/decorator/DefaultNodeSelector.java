package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import hudson.Extension;
import io.fabric8.kubernetes.api.model.Pod;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

import javax.annotation.Nonnull;
import java.util.Collections;

/**
 * Sets the default node selector to linux if it hasn't been set explicitly in the pod before.
 */
@Extension
public class DefaultNodeSelector implements PodDecorator {
    @Nonnull
    @Override
    public Pod decorate(@Nonnull KubernetesCloud kubernetesCloud, @Nonnull Pod pod) {
        // default OS: https://kubernetes.io/docs/concepts/configuration/assign-pod-node/
        if (pod.getSpec().getRuntimeClassName() == null &&
                (pod.getSpec().getNodeSelector() == null || pod.getSpec().getNodeSelector().isEmpty()) &&
                (pod.getSpec().getAffinity() == null || pod.getSpec().getAffinity().getNodeAffinity() == null)) {
            pod.getSpec().setNodeSelector(Collections.singletonMap("kubernetes.io/os", "linux"));
        }
        return pod;
    }
}
