package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Label;
import hudson.slaves.NodeProvisioner;

/**
 * A builder of {@link hudson.slaves.NodeProvisioner.PlannedNode} implementations for Kubernetes.
 * Can be subclassed to provide alternative implementations of {@link hudson.slaves.NodeProvisioner.PlannedNode}.
 */
public abstract class PlannedNodeBuilder {
    private KubernetesCloud cloud;
    private PodTemplate template;
    private Label label;
    private int numExecutors = 1;

    /**
     * Returns the {@link KubernetesCloud}.
     * @return the {@link KubernetesCloud}.
     */
    public KubernetesCloud getCloud() {
        return cloud;
    }

    /**
     * Returns the {@link PodTemplate}.
     * @return
     */
    public PodTemplate getTemplate() {
        return template;
    }

    public Label getLabel() {
        return label;
    }

    public int getNumExecutors() {
        return numExecutors;
    }

    /**
     * @param cloud the {@link KubernetesCloud} instance to use.
     * @return the current builder.
     */
    public PlannedNodeBuilder cloud(KubernetesCloud cloud) {
        this.cloud = cloud;
        return this;
    }

    /**
     * @param template the {@link PodTemplate} instance to use.
     * @return the current builder.
     */
    public PlannedNodeBuilder template(PodTemplate template) {
        this.template = template;
        return this;
    }

    /**
     * @param label the {@link Label} to use.
     * @return the current builder.
     */
    public PlannedNodeBuilder label(Label label) {
        this.label = label;
        return this;
    }

    /**
     * @param numExecutors the number of executors.
     * @return the current builder.
     */
    public PlannedNodeBuilder numExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
        return this;
    }

    /**
     * Builds the {@link hudson.slaves.NodeProvisioner.PlannedNode} instance based on the given inputs.
     * @return a {@link hudson.slaves.NodeProvisioner.PlannedNode} configured from this builder.
     */
    public abstract NodeProvisioner.PlannedNode build();
}
