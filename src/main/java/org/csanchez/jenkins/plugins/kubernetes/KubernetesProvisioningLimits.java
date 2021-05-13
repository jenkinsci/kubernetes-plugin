package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Node;
import hudson.model.Queue;
import jenkins.metrics.api.Metrics;
import jenkins.model.Jenkins;
import jenkins.model.NodeListener;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements provisioning limits for clouds and pod templates
 */
@Extension
public final class KubernetesProvisioningLimits {
    private static final Logger LOGGER = Logger.getLogger(KubernetesProvisioningLimits.class.getName());

    /**
     * Tracks current number of kubernetes agents per pod template
     */
    private final Map<String, AtomicInteger> podTemplateCounts = Collections.synchronizedMap(new HashMap<>());

    /**
     * Tracks current number of kubernetes agents per kubernetes cloud
     */
    private final Map<String, AtomicInteger> cloudCounts = Collections.synchronizedMap(new HashMap<>());

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void init() {
        // We don't want anything to be provisioned while we do the initial count.
        Queue.withLock(() -> {
            final KubernetesProvisioningLimits instance = get();
            Jenkins.get().getNodes()
                    .stream()
                    .filter(KubernetesSlave.class::isInstance)
                    .map(KubernetesSlave.class::cast)
                    .forEach(node -> {
                instance.getGlobalCount(node.getCloudName()).addAndGet(node.getNumExecutors());
                instance.getPodTemplateCount(node.getTemplateId()).addAndGet(node.getNumExecutors());
            });
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
    @SuppressFBWarnings(value="JLM_JSR166_UTILCONCURRENT_MONITORENTER", justification = "Trust me here")
    public boolean register(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate podTemplate, int numExecutors) {
        AtomicInteger globalCount = getGlobalCount(cloud.name);
        AtomicInteger podTemplateCount = getPodTemplateCount(podTemplate.getId());
        synchronized (globalCount) {
            synchronized (podTemplateCount) {
                if (globalCount.get() + numExecutors <= cloud.getContainerCap()) {
                    if (podTemplateCount.get() + numExecutors <= podTemplate.getInstanceCap()) {
                        int g = globalCount.addAndGet(numExecutors);
                        int p = podTemplateCount.addAndGet(numExecutors);
                        LOGGER.log(Level.FINEST, () -> cloud.name + " global limit: " + g + "/" + cloud.getContainerCap());
                        LOGGER.log(Level.FINEST, () -> podTemplate.getName() + " template limit: " + p + "/" + podTemplate.getInstanceCap());
                        return true;
                    } else {
                        Metrics.metricRegistry().counter(MetricNames.REACHED_POD_CAP).inc();
                    }
                } else {
                    Metrics.metricRegistry().counter(MetricNames.REACHED_GLOBAL_CAP).inc();
                }
            }
        }
        return false;
    }

    /**
     * Unregisters executors, when an agent is terminated
     * @param cloud the kubernetes cloud the executors were on
     * @param podTemplate the pod template used to schedule the agent
     * @param numExecutors the number of executors (pretty much always 1)
     */
    @SuppressFBWarnings(value="JLM_JSR166_UTILCONCURRENT_MONITORENTER", justification = "Trust me here")
    public void unregister(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate podTemplate, int numExecutors) {
        AtomicInteger globalCount = getGlobalCount(cloud.name);
        AtomicInteger podTemplateCount = getPodTemplateCount(podTemplate.getId());
        synchronized (globalCount) {
            synchronized (podTemplateCount) {
                int newGlobalCount = globalCount.addAndGet(numExecutors * -1);
                int newPodTemplateCount = podTemplateCount.addAndGet(numExecutors * -1);
                if (newGlobalCount < 0) {
                    LOGGER.log(Level.WARNING, "Global count for " + cloud.name + " went below zero. There is likely a bug in kubernetes-plugin");
                    globalCount.set(0);
                } else {
                    LOGGER.log(Level.FINEST, () -> cloud.name + " global limit: " + newGlobalCount + "/" + cloud.getContainerCap());
                }
                if (newPodTemplateCount < 0) {
                    LOGGER.log(Level.WARNING, "Pod template count for " + podTemplate.getId() + " went below zero. There is likely a bug in kubernetes-plugin");
                    podTemplateCount.set(0);
                } else {
                    LOGGER.log(Level.FINEST, () -> podTemplate.getName() + " template limit: " + newPodTemplateCount + "/" + podTemplate.getInstanceCap());
                }
            }
        }
    }

    @Nonnull
    AtomicInteger getGlobalCount(String name) {
        return cloudCounts.computeIfAbsent(name, k -> new AtomicInteger());
    }

    @Nonnull
    AtomicInteger getPodTemplateCount(String id) {
        return podTemplateCounts.computeIfAbsent(id, k -> new AtomicInteger());
    }

    @Extension
    public static class NodeListenerImpl extends NodeListener {
        @Override
        protected void onDeleted(@Nonnull Node node) {
            if (node instanceof KubernetesSlave) {
                KubernetesSlave kubernetesNode = (KubernetesSlave) node;
                KubernetesProvisioningLimits.get().unregister(kubernetesNode.getKubernetesCloud(), kubernetesNode.getTemplate(), node.getNumExecutors());
            }
        }
    }

}
