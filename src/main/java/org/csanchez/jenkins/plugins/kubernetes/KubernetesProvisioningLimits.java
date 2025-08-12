package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Node;
import hudson.model.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.metrics.api.Metrics;
import jenkins.model.Jenkins;
import jenkins.model.NodeListener;
import net.jcip.annotations.GuardedBy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Implements provisioning limits for clouds and pod templates
 */
@Extension
public final class KubernetesProvisioningLimits {
    private static final Logger LOGGER = Logger.getLogger(KubernetesProvisioningLimits.class.getName());

    @GuardedBy("this")
    private final AtomicBoolean init = new AtomicBoolean();

    /**
     * Tracks current number of kubernetes agents per pod template
     */
    private final ConcurrentMap<String, Integer> podTemplateCounts = new ConcurrentHashMap<>();

    /**
     * Tracks current number of kubernetes agents per kubernetes cloud
     */
    private final ConcurrentMap<String, Integer> cloudCounts = new ConcurrentHashMap<>();

    /**
     * Initialize limits counter
     * @return whether the instance was already initialized before this call.
     */
    private boolean initInstance() {
        if (!init.getAndSet(true)) {
            Queue.withLock(() -> {
                Jenkins.get().getNodes().stream()
                        .filter(KubernetesSlave.class::isInstance)
                        .map(KubernetesSlave.class::cast)
                        .forEach(node -> {
                            cloudCounts.put(
                                    node.getCloudName(), getGlobalCount(node.getCloudName()) + node.getNumExecutors());
                            podTemplateCounts.put(
                                    node.getTemplateId(),
                                    getPodTemplateCount(node.getTemplateId()) + node.getNumExecutors());
                        });
            });
            return false;
        } else {
            return true;
        }
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
    public boolean register(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate, int numExecutors) {
        initInstance();
        int newGlobalCount = getGlobalCount(cloud.name) + numExecutors;
        if (newGlobalCount <= cloud.getContainerCap()) {
            int newPodTemplateCount = getPodTemplateCount(podTemplate.getId()) + numExecutors;
            if (newPodTemplateCount <= podTemplate.getInstanceCap()) {
                cloudCounts.put(cloud.name, newGlobalCount);
                LOGGER.log(
                        Level.FINEST,
                        () -> cloud.name + " global limit: " + newGlobalCount + "/" + cloud.getContainerCap());

                podTemplateCounts.put(podTemplate.getId(), newPodTemplateCount);
                LOGGER.log(
                        Level.FINEST,
                        () -> podTemplate.getName() + " template limit: " + newPodTemplateCount + "/"
                                + podTemplate.getInstanceCap());
                return true;
            } else {
                LOGGER.log(
                        Level.FINEST,
                        () -> podTemplate.getName() + " template limit reached: "
                                + getPodTemplateCount(podTemplate.getId()) + "/" + podTemplate.getInstanceCap()
                                + ". Cannot add " + numExecutors + " more!");
                Metrics.metricRegistry().counter(MetricNames.REACHED_POD_CAP).inc();
            }
        } else {
            LOGGER.log(
                    Level.FINEST,
                    () -> cloud.name + " global limit reached: " + getGlobalCount(cloud.name) + "/"
                            + cloud.getContainerCap() + ". Cannot add " + numExecutors + " more!");
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
    public void unregister(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate, int numExecutors) {
        if (initInstance()) {
            int newGlobalCount = getGlobalCount(cloud.name) - numExecutors;
            if (newGlobalCount < 0) {
                LOGGER.log(
                        Level.WARNING,
                        "Global count for " + cloud.name
                                + " went below zero. There is likely a bug in kubernetes-plugin");
            }
            cloudCounts.put(cloud.name, Math.max(0, newGlobalCount));
            LOGGER.log(
                    Level.FINEST,
                    () -> cloud.name + " global limit: " + Math.max(0, newGlobalCount) + "/" + cloud.getContainerCap());

            int newPodTemplateCount = getPodTemplateCount(podTemplate.getId()) - numExecutors;
            if (newPodTemplateCount < 0) {
                LOGGER.log(
                        Level.WARNING,
                        "Pod template count for " + podTemplate.getName()
                                + " went below zero. There is likely a bug in kubernetes-plugin");
            }
            podTemplateCounts.put(podTemplate.getId(), Math.max(0, newPodTemplateCount));
            LOGGER.log(
                    Level.FINEST,
                    () -> podTemplate.getName() + " template limit: " + Math.max(0, newPodTemplateCount) + "/"
                            + podTemplate.getInstanceCap());
        }
    }

    @NonNull
    @Restricted(NoExternalUse.class)
    int getGlobalCount(String cloudName) {
        return cloudCounts.getOrDefault(cloudName, 0);
    }

    @NonNull
    @Restricted(NoExternalUse.class)
    int getPodTemplateCount(String podTemplate) {
        return podTemplateCounts.getOrDefault(podTemplate, 0);
    }

    @Extension
    public static class NodeListenerImpl extends NodeListener {
        @Override
        protected void onDeleted(@NonNull Node node) {
            if (node instanceof KubernetesSlave) {
                KubernetesProvisioningLimits instance = KubernetesProvisioningLimits.get();
                KubernetesSlave kubernetesNode = (KubernetesSlave) node;
                PodTemplate template = kubernetesNode.getTemplateOrNull();
                if (template != null) {
                    instance.unregister(kubernetesNode.getKubernetesCloud(), template, node.getNumExecutors());
                }
            }
        }
    }
}
