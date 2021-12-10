package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.stream.Collectors.toSet;

import java.util.Set;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;

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
    @NonNull
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
    @NonNull
    public abstract Set<String> getInProvisioning(Label label);
}
