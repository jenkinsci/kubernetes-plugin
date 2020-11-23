package org.csanchez.jenkins.plugins.kubernetes;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;

import javax.annotation.Nonnull;

/**
 * Allows to alter a pod definition after it has been built from the yaml and DSL/GUI configuration.
 */
public interface PodDecorator extends ExtensionPoint {

    @Nonnull
    static Pod decorateAll(@Nonnull Pod pod) {
        ExtensionList<PodDecorator> all = ExtensionList.lookup(PodDecorator.class);
        if (all.isEmpty()) {
            return pod;
        } else {
            PodBuilder builder = new PodBuilder(pod);
            for (PodDecorator decorator : all) {
                builder = decorator.decorate(builder);
            }
            return builder.build();
        }

    }

    @Nonnull
    PodBuilder decorate(@Nonnull PodBuilder builder);
}
