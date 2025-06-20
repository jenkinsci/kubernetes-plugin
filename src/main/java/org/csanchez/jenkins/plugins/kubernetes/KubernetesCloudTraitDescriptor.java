package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Descriptor;
import java.util.Optional;

/**
 * Descriptor base type for {@link KubernetesCloudTrait} implementations.
 */
public abstract class KubernetesCloudTraitDescriptor extends Descriptor<KubernetesCloudTrait> {

    /**
     * Get default trait configuration for a new {@link KubernetesCloud} instance.
     *
     * @return optional default trait configuration
     */
    public Optional<KubernetesCloudTrait> getDefaultTrait() {
        return Optional.empty();
    }
}
