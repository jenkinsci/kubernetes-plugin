package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import hudson.Extension;
import io.fabric8.kubernetes.api.model.Pod;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;

/**
 * Sets the restart policy to Never.
 */
@Extension
public class DefaultRestartPolicy implements PodDecorator {
    @Nonnull
    @Override
    public Pod decorate(@Nonnull Pod pod) {
        if (StringUtils.isBlank(pod.getSpec().getRestartPolicy())) {
            pod.getSpec().setRestartPolicy("Never");
        }
        return pod;
    }
}
