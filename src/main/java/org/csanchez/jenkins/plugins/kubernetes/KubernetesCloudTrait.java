package org.csanchez.jenkins.plugins.kubernetes;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import java.util.List;
import java.util.Optional;

/**
 * Extension point for {@link KubernetesCloud} configuration traits.
 */
public abstract class KubernetesCloudTrait extends AbstractDescribableImpl<KubernetesCloudTrait>
        implements ExtensionPoint {

    /**
     * @return all the {@link KubernetesCloudTrait} descriptor instances.
     */
    public static ExtensionList<KubernetesCloudTraitDescriptor> all() {
        return ExtensionList.lookup(KubernetesCloudTraitDescriptor.class);
    }

    public static List<KubernetesCloudTrait> getDefaultTraits() {
        return all().stream()
                .map(KubernetesCloudTraitDescriptor::getDefaultTrait)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
