package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * A source of pod templates.
 */
public abstract class PodTemplateSource implements ExtensionPoint {
    public static List<PodTemplate> getAll(@Nonnull KubernetesCloud cloud) {
        return ExtensionList.lookup(PodTemplateSource.class)
                .stream()
                .map(s -> s.getList(cloud))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    /**
     * The list of {@link PodTemplate} contributed by this implementation.
     * @return The list of {@link PodTemplate} contributed by this implementation.
     * @param cloud
     */
    @Nonnull
    protected abstract List<PodTemplate> getList(@Nonnull KubernetesCloud cloud);
}
