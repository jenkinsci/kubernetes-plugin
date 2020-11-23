package org.csanchez.jenkins.plugins.kubernetes;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.fabric8.kubernetes.api.model.PodBuilder;

import javax.annotation.Nonnull;

/**
 * Allows to alter a pod definition after it has been built from the yaml and DSL/GUI configuration.
 */
public abstract class PodDecorator implements ExtensionPoint {
    public static ExtensionList<PodDecorator> all() {
        return ExtensionList.lookup(PodDecorator.class);
    }

    @Nonnull
    public static PodBuilder decorateAll(@Nonnull PodBuilder builder) {
        for (PodDecorator decorator : all()) {
            builder = decorator.decorate(builder);
        }
        return builder;
    }

    @Nonnull
    protected abstract PodBuilder decorate(@Nonnull PodBuilder builder);
}
