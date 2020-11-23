package org.csanchez.jenkins.plugins.kubernetes;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.fabric8.kubernetes.api.model.PodBuilder;

import javax.annotation.Nonnull;

/**
 * Allows to alter a pod definition after it has been built from the yaml and DSL/GUI configuration.
 */
public interface PodDecorator extends ExtensionPoint {

    @Nonnull
    static PodBuilder decorateAll(@Nonnull PodBuilder builder) {
        for (PodDecorator decorator : ExtensionList.lookup(PodDecorator.class)) {
            builder = decorator.decorate(builder);
        }
        return builder;
    }

    @Nonnull
    PodBuilder decorate(@Nonnull PodBuilder builder);
}
