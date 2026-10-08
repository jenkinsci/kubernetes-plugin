package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import org.csanchez.jenkins.plugins.kubernetes.PlannedNodeBuilder;
import org.csanchez.jenkins.plugins.kubernetes.PlannedNodeBuilderFactory;
import org.jenkinsci.plugins.variant.OptionalExtension;

/**
 * Supplies a {@link TrackedPlannedNodeBuilder} when the cloud-stats plugin is installed, so the default
 * provisioning path records a Cloud Statistics activity (JENKINS-67256).
 *
 * <p>Registered at a negative ordinal so any downstream {@link PlannedNodeBuilderFactory} (which
 * defaults to ordinal 0) still takes precedence over this one. When cloud-stats is absent this extension
 * does not register and the core falls back to the plain {@code StandardPlannedNodeBuilder}.
 */
@OptionalExtension(requirePlugins = "cloud-stats", ordinal = -1)
public class TrackedPlannedNodeBuilderFactory extends PlannedNodeBuilderFactory {
    @Override
    public PlannedNodeBuilder newInstance() {
        return new TrackedPlannedNodeBuilder();
    }
}
