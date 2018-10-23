package org.csanchez.jenkins.plugins.kubernetes;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * A factory of {@link KubernetesComputer} instances.
 */
public abstract class KubernetesComputerFactory implements ExtensionPoint {
    /**
     * Returns all registered implementations of {@link KubernetesComputerFactory}.
     * @return all registered implementations of {@link KubernetesComputerFactory}.
     */
    public static ExtensionList<KubernetesComputerFactory> all() {
        return ExtensionList.lookup(KubernetesComputerFactory.class);
    }

    /**
     * Returns a new instance of {@link KubernetesComputer}.
     * @return a new instance of {@link KubernetesComputer}.
     */
    public static KubernetesComputer createInstance(KubernetesSlave slave) {
        for (KubernetesComputerFactory factory: all()) {
            KubernetesComputer kubernetesComputer = factory.newInstance(slave);
            if (kubernetesComputer != null) {
                return kubernetesComputer;
            }
        }
        return new KubernetesComputer(slave);
    }

    /**
     * Creates a new instance of {@link KubernetesComputer}.
     * @return a new instance of {@link KubernetesComputer}.
     */
    public abstract KubernetesComputer newInstance(KubernetesSlave slave);
}
