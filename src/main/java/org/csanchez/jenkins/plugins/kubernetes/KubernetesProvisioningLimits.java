package org.csanchez.jenkins.plugins.kubernetes;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Node;
import hudson.model.Queue;
import jenkins.metrics.api.Metrics;
import jenkins.model.Jenkins;
import jenkins.model.NodeListener;

/**
 * Implements provisioning limits for clouds and pod templates
 */
@Extension
public final class KubernetesProvisioningLimits {
    private static final Logger LOGGER = Logger.getLogger(KubernetesProvisioningLimits.class.getName());

    /**
     * Tracks current number of kubernetes agents per pod template
     */
    private final Map<String, Integer> podTemplateCounts = new HashMap<>();

    /**
     * Tracks current number of kubernetes agents per kubernetes cloud
     */
    private final Map<String, Integer> cloudCounts = new HashMap<>();

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void init() {
        // We don't want anything to be provisioned while we do the initial count.
        Queue.withLock(() -> {
            final KubernetesProvisioningLimits instance = get();
            synchronized(instance) {
                Jenkins.get().getNodes()
                        .stream()
                        .filter(KubernetesSlave.class::isInstance)
                        .map(KubernetesSlave.class::cast)
                        .forEach(node -> {
                    instance.cloudCounts.put(node.getCloudName(), instance.getGlobalCount(node.getCloudName()) + node.getNumExecutors());
                    instance.podTemplateCounts.put(node.getTemplateId(), instance.getPodTemplateCount(node.getTemplateId()) + node.getNumExecutors());
                });
            }
        });
    }

    /**
     * @return the singleton instance
     */
    public static KubernetesProvisioningLimits get() {
        return ExtensionList.lookupSingleton(KubernetesProvisioningLimits.class);
    }

    /**
     * Register executors
     * @param cloud the kubernetes cloud the executors will be on
     * @param podTemplate the pod template used to schedule the agent
     * @param numExecutors the number of executors (pretty much always 1)
     */
    public synchronized boolean register(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate podTemplate, int numExecutors) {
        int newGlobalCount = getGlobalCount(cloud.name) + numExecutors;
        if (newGlobalCount <= cloud.getContainerCap()) {
            int newPodTemplateCount = getPodTemplateCount(podTemplate.getId()) + numExecutors;
            if (newPodTemplateCount <= podTemplate.getInstanceCap()) {
                cloudCounts.put(cloud.name, newGlobalCount);
                LOGGER.log(Level.FINEST, () -> cloud.name + " global limit: " + newGlobalCount + "/" + cloud.getContainerCap());

                podTemplateCounts.put(podTemplate.getId(), newPodTemplateCount);
                LOGGER.log(Level.FINEST, () -> podTemplate.getName() + " template limit: " + newPodTemplateCount + "/" + podTemplate.getInstanceCap());
                return true;
            } else {
                LOGGER.log(Level.FINEST, () -> podTemplate.getName() + " template limit reached: " + getPodTemplateCount(podTemplate.getId()) + "/" + podTemplate.getInstanceCap() + ". Cannot add " + numExecutors + " more!");
                Metrics.metricRegistry().counter(MetricNames.REACHED_POD_CAP).inc();
            }
        } else {
            LOGGER.log(Level.FINEST, () -> cloud.name + " global limit reached: " + getGlobalCount(cloud.name) + "/" + cloud.getContainerCap() + ". Cannot add " + numExecutors + " more!");
            Metrics.metricRegistry().counter(MetricNames.REACHED_GLOBAL_CAP).inc();
        }
        return false;
    }

    /**
     * Unregisters executors, when an agent is terminated
     * @param cloud the kubernetes cloud the executors were on
     * @param podTemplate the pod template used to schedule the agent
     * @param numExecutors the number of executors (pretty much always 1)
     */
    public synchronized void unregister(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate podTemplate, int numExecutors) {
        int newGlobalCount = getGlobalCount(cloud.name) - numExecutors;
        if (newGlobalCount < 0) {
            LOGGER.log(Level.WARNING, "Global count for " + cloud.name + " went below zero. There is likely a bug in kubernetes-plugin");
        }
        cloudCounts.put(cloud.name, Math.max(0, newGlobalCount));
        LOGGER.log(Level.FINEST, () -> cloud.name + " global limit: " + Math.max(0, newGlobalCount) + "/" + cloud.getContainerCap());

        int newPodTemplateCount = getPodTemplateCount(podTemplate.getId()) - numExecutors;
        if (newPodTemplateCount < 0) {
            LOGGER.log(Level.WARNING, "Pod template count for " + podTemplate.getName() + " went below zero. There is likely a bug in kubernetes-plugin");
        }
        podTemplateCounts.put(podTemplate.getId(), Math.max(0, newPodTemplateCount));
        LOGGER.log(Level.FINEST, () -> podTemplate.getName() + " template limit: " + Math.max(0, newPodTemplateCount) + "/" + podTemplate.getInstanceCap());
    }

    @Nonnull
    @Restricted(NoExternalUse.class)
    int getGlobalCount(String cloudName) {
        return cloudCounts.getOrDefault(cloudName, 0);
    }

    @Nonnull
    @Restricted(NoExternalUse.class)
    int getPodTemplateCount(String podTemplate) {
        return podTemplateCounts.getOrDefault(podTemplate, 0);
    }

    @Extension
    public static class NodeListenerImpl extends NodeListener {
        @Override
        protected void onDeleted(@Nonnull Node node) {
            if (node instanceof KubernetesSlave) {
                KubernetesSlave kubernetesNode = (KubernetesSlave) node;
                PodTemplate template = kubernetesNode.getTemplateOrNull();
                if (template != null) {
                    KubernetesProvisioningLimits.get().unregister(kubernetesNode.getKubernetesCloud(), template, node.getNumExecutors());
                }
            }
        }
    }

}
