package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.stream.Collectors.toSet;

import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;
import hudson.model.Node;

/**
 * Collects the Kubernetes agents currently in provisioning.
 */
public abstract class InProvisioning implements ExtensionPoint {
    /**
     * Returns the agents names in provisioning according to all implementations of this extension point for the given label.
     *
     * @param label the {@link Label} being checked.
     * @return the agents names in provisioning according to all implementations of this extension point for the given label.
     */
    @Nonnull
    public static Set<String> getAllInProvisioning(@CheckForNull Label label) {
        return all().stream()
                .flatMap(c -> c.getInProvisioning(label).stream())
                .collect(toSet());
    }

    public static ExtensionList<InProvisioning> all() {
        return ExtensionList.lookup(InProvisioning.class);
    }

    /**
     * Returns the agents in provisioning for the current label.
     *
     * @param label The label being checked
     * @return The agents names in provisioning for the current label.
     */
    @Nonnull
    public abstract Set<String> getInProvisioning(Label label);
}
