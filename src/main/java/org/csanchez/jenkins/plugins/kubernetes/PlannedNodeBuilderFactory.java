package org.csanchez.jenkins.plugins.kubernetes;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * A factory of {@link PlannedNodeBuilder} instances.
 */
public abstract class PlannedNodeBuilderFactory implements ExtensionPoint {
    /**
     * Returns all registered implementations of {@link PlannedNodeBuilderFactory}.
     * @return all registered implementations of {@link PlannedNodeBuilderFactory}.
     */
    public static ExtensionList<PlannedNodeBuilderFactory> all() {
        return ExtensionList.lookup(PlannedNodeBuilderFactory.class);
    }

    /**
     * Returns a new instance of {@link PlannedNodeBuilder}.
     * @return a new instance of {@link PlannedNodeBuilder}.
     */
    public static PlannedNodeBuilder createInstance() {
        for (PlannedNodeBuilderFactory factory: all()) {
            PlannedNodeBuilder plannedNodeBuilder = factory.newInstance();
            if (plannedNodeBuilder != null) {
                return plannedNodeBuilder;
            }
        }
        return new StandardPlannedNodeBuilder();
    }

    /**
     * Creates a new instance of {@link PlannedNodeBuilder}.
     * @return a new instance of {@link PlannedNodeBuilder}.
     */
    public abstract PlannedNodeBuilder newInstance();
}
