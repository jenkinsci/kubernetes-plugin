package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import io.fabric8.kubernetes.api.model.Pod;

/**
 * <code>PodRetention</code> instances determine if the Kubernetes pod running a Jenkins slave
 * should be deleted after Jenkins terminates the slave.
 * 
 * <p>Custom pod retention behavior can be added by extending this class, including a descriptor
 * that extends {@link PodRetentionDescriptor}</p>
 */
public abstract class PodRetention extends AbstractDescribableImpl<PodRetention> implements ExtensionPoint {

    /**
     * Returns the default <code>PodRetention</code> for a <code>KubernetesCloud</code> instance.
     * 
     * @return the {@link Never} <code>PodRetention</code> strategy.
     */
    public static PodRetention getKubernetesCloudDefault() {
        return new Never();
    }

    /**
     * Returns the default <code>PodRetention</code> for a <code>PodTemplate</code> instance.
     * 
     * @return the {@link Default} <code>PodRetention</code> strategy.
     */
    public static PodRetention getPodTemplateDefault() {
        return new Default();
    }

    /**
     * Determines if a slave pod should be deleted after the Jenkins build completes.
     * 
     * @param cloud - the {@link KubernetesCloud} the slave pod belongs to.
     * @param pod - the {@link Pod} running the Jenkins build.
     * 
     * @return <code>true</code> if the slave pod should be deleted.
     */
    public abstract boolean shouldDeletePod(KubernetesCloud cloud, Pod pod);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

}
