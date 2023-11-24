package org.csanchez.jenkins.plugins.kubernetes;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.jcip.annotations.GuardedBy;
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

    @GuardedBy("this")
    private boolean init;

    /**
     * Tracks current number of kubernetes agents per pod template
     */
    private final Map<String, Integer> podTemplateCounts = new HashMap<>();

    /**
     * Tracks current number of kubernetes agents per kubernetes cloud
     */
    private final Map<String, Integer> cloudCounts = new HashMap<>();

    /**
     * Initialize limits counter
     * @return whether the instance was already initialized before this call.
     */
    private synchronized boolean initInstance() {
        boolean previousInit = init;
        if (!init) {
            Queue.withLock(() -> {
                Jenkins.get().getNodes()
                        .stream()
                        .filter(KubernetesSlave.class::isInstance)
                        .map(KubernetesSlave.class::cast)
                        .forEach(node -> {
                            cloudCounts.put(node.getCloudName(), getGlobalCount(node.getCloudName()) + node.getNumExecutors());
                            podTemplateCounts.put(node.getTemplateId(), getPodTemplateCount(node.getTemplateId()) + node.getNumExecutors());
                        });
            });
            init = true;
        }
        return previousInit;
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
    public synchronized boolean register(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate, int numExecutors) {
        initInstance();
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
    public synchronized void unregister(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate, int numExecutors) {
        if (initInstance()) {
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
        private static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        @Override
        protected void onDeleted(@NonNull Node node) {
            if (node instanceof KubernetesSlave) {
                KubernetesNodeDeletedTask task = new KubernetesNodeDeletedTask((KubernetesSlave)node);
                executor.execute(task);
            }
        }
    }

}
