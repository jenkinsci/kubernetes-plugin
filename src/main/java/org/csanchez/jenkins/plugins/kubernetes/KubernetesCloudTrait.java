package org.csanchez.jenkins.plugins.kubernetes;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.util.Optional;
import jenkins.model.Jenkins;

/**
 * Extension point for {@link KubernetesCloud} configuration traits.
 */
public abstract class KubernetesCloudTrait extends AbstractDescribableImpl<KubernetesCloudTrait>
        implements ExtensionPoint {

    /**
     * Returns all the {@link KubernetesCloudTrait} descriptor instances.
     *
     * @return all the {@link KubernetesCloudTrait} descriptor instances.
     */
    public static DescriptorExtensionList<KubernetesCloudTrait, Descriptor<KubernetesCloudTrait>> all() {
        return Jenkins.get().getDescriptorList(KubernetesCloudTrait.class);
    }

    /**
     * Descriptor base type for {@link KubernetesCloudTrait} implementations.
     */
    public abstract static class KubernetesCloudTraitDescriptor extends Descriptor<KubernetesCloudTrait> {

        /**
         * Get default trait configuration for a new {@link KubernetesCloud} instance.
         * @return optional default trait configuration
         */
        public Optional<KubernetesCloudTrait> getDefaultTrait() {
            return Optional.empty();
        }
    }
}
