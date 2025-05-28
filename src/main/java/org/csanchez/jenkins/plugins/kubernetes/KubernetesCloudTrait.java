package org.csanchez.jenkins.plugins.kubernetes;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
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
}
