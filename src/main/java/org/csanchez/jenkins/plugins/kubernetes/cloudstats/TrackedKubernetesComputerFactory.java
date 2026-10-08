package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputerFactory;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.variant.OptionalExtension;

/**
 * Supplies a {@link TrackedKubernetesComputer} when the cloud-stats plugin is installed, so a
 * Kubernetes agent's computer is a Cloud Statistics {@code TrackedItem} and its lifecycle is recorded
 * (JENKINS-67256). When cloud-stats is absent this extension does not register and the core falls back
 * to a plain {@link KubernetesComputer}.
 */
@OptionalExtension(requirePlugins = "cloud-stats")
public class TrackedKubernetesComputerFactory extends KubernetesComputerFactory {
    @Override
    public KubernetesComputer newInstance(KubernetesSlave slave) {
        return new TrackedKubernetesComputer(slave);
    }
}
