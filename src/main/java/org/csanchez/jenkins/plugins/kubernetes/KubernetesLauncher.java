/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.csanchez.jenkins.plugins.kubernetes;


import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.OfflineCause;
import hudson.slaves.SlaveComputer;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.metrics.api.Metrics;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Reaper;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Launches on Kubernetes the specified {@link KubernetesComputer} instance.
 */
public class KubernetesLauncher extends JNLPLauncher {
    // Report progress every 30 seconds
    private static final long REPORT_INTERVAL = TimeUnit.SECONDS.toMillis(30L);

    private static final Collection<String> POD_TERMINATED_STATES =
            Collections.unmodifiableCollection(Arrays.asList("Succeeded", "Failed"));

    private static final Logger LOGGER = Logger.getLogger(KubernetesLauncher.class.getName());

    private boolean launched;

    /**
     * Provisioning exception if any.
     */
    @CheckForNull
    private transient Throwable problem;

    @DataBoundConstructor
    public KubernetesLauncher(String tunnel, String vmargs) {
        super(tunnel, vmargs);
    }

    public KubernetesLauncher() {
        super();
    }

    @Override
    public boolean isLaunchSupported() {
        return !launched;
    }

    @Override
    @SuppressFBWarnings(value = "SWL_SLEEP_WITH_LOCK_HELD", justification = "This is fine")
    public synchronized void launch(SlaveComputer computer, TaskListener listener) {
        if (!(computer instanceof KubernetesComputer)) {
            throw new IllegalArgumentException("This Launcher can be used only with KubernetesComputer");
        }
        // Activate reaper if it never got activated.
        Reaper.getInstance().maybeActivate();
        KubernetesComputer kubernetesComputer = (KubernetesComputer) computer;
        computer.setAcceptingTasks(false);
        KubernetesSlave node = kubernetesComputer.getNode();
        if (node == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }
        if (launched) {
            LOGGER.log(INFO, "Agent has already been launched, activating: {0}", node.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        String cloudName = node.getCloudName();
        final PodTemplate template = node.getTemplate();
        TaskListener runListener = TaskListener.NULL;
        try {
            KubernetesCloud cloud = node.getKubernetesCloud();
            KubernetesClient client = cloud.connect();
            Pod pod = template.build(node);
            node.assignPod(pod);

            String podName = pod.getMetadata().getName();

            String namespace = Arrays.asList( //
                    pod.getMetadata().getNamespace(),
                    template.getNamespace(), client.getNamespace()) //
                    .stream().filter(s -> StringUtils.isNotBlank(s)).findFirst().orElse(null);
            node.setNamespace(namespace);


            runListener = template.getListener();

            LOGGER.log(FINE, () -> "Creating Pod: " + cloudName + " " + namespace + "/" + podName);
            try {
                pod = client.pods().inNamespace(namespace).create(pod);
            } catch (KubernetesClientException e) {
                Metrics.metricRegistry().counter(MetricNames.CREATION_FAILED).inc();
                int httpCode = e.getCode();
                if (400 <= httpCode && httpCode < 500) { // 4xx
                    if (httpCode == 403 && e.getMessage().contains("is forbidden: exceeded quota")) {
                        runListener.getLogger().printf("WARNING: Unable to create pod: %s %s/%s because kubernetes resource quota exceeded. %n%s%nRetrying...%n%n",
                                cloudName, namespace, pod.getMetadata().getName(), e.getMessage());
                    }
                    else if (httpCode == 409 && e.getMessage().contains("Operation cannot be fulfilled on resourcequotas")) {
                        // See: https://github.com/kubernetes/kubernetes/issues/67761 ; A retry usually works.
                        runListener.getLogger().printf("WARNING: Unable to create pod: %s %s/%s because kubernetes resource quota update conflict. %n%s%nRetrying...%n%n",
                                cloudName, namespace, pod.getMetadata().getName(), e.getMessage());
                    }
                    else {
                        runListener.getLogger().printf("ERROR: Unable to create pod %s %s/%s.%n%s%n", cloudName, namespace, pod.getMetadata().getName(), e.getMessage());
                        PodUtils.cancelQueueItemFor(pod, e.getMessage());
                    }
                } else if (500 <= httpCode && httpCode < 600) { // 5xx
                    LOGGER.log(FINE,"Kubernetes returned HTTP code {0} {1}. Retrying...", new Object[] {e.getCode(), e.getStatus()});
                } else {
                    LOGGER.log(WARNING, "Kubernetes returned unhandled HTTP code {0} {1}", new Object[] {e.getCode(), e.getStatus()});
                }
                throw e;
            }
            LOGGER.log(INFO, () -> "Created Pod: " + cloudName + " " + namespace + "/" + podName);
            listener.getLogger().printf("Created Pod: %s %s/%s%n", cloudName, namespace, podName);
            Metrics.metricRegistry().counter(MetricNames.PODS_CREATED).inc();

            runListener.getLogger().printf("Created Pod: %s %s/%s%n", cloudName, namespace, podName);
            kubernetesComputer.setLaunching(true);

            ObjectMeta podMetadata = pod.getMetadata();
            template.getWorkspaceVolume().createVolume(client, podMetadata);
            template.getVolumes().forEach(volume -> volume.createVolume(client, podMetadata));

            client.pods().inNamespace(namespace).withName(podName).waitUntilReady(template.getSlaveConnectTimeout(), TimeUnit.SECONDS);

            LOGGER.log(INFO, () -> "Pod is running: " + cloudName + " " + namespace + "/" + podName);

            // We need the pod to be running and connected before returning
            // otherwise this method keeps being called multiple times
            // so wait for agent to be online
            int waitForSlaveToConnect = template.getSlaveConnectTimeout();
            int waitedForSlave;

            SlaveComputer slaveComputer = null;
            String status = null;
            List<ContainerStatus> containerStatuses = null;
            long lastReportTimestamp = System.currentTimeMillis();
            for (waitedForSlave = 0; waitedForSlave < waitForSlaveToConnect; waitedForSlave++) {
                slaveComputer = node.getComputer();
                if (slaveComputer == null) {
                    Metrics.metricRegistry().counter(MetricNames.LAUNCH_FAILED).inc();
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (slaveComputer.isOnline()) {
                    break;
                }

                // Check that the pod hasn't failed already
                pod = client.pods().inNamespace(namespace).withName(podName).get();
                if (pod == null) {
                    Metrics.metricRegistry().counter(MetricNames.LAUNCH_FAILED).inc();
                    throw new IllegalStateException("Pod no longer exists: " + podName);
                }
                status = pod.getStatus().getPhase();
                if (POD_TERMINATED_STATES.contains(status)) {
                    Metrics.metricRegistry().counter(MetricNames.LAUNCH_FAILED).inc();
                    Metrics.metricRegistry().counter(MetricNames.metricNameForPodStatus(status)).inc();
                    logLastLines(containerStatuses, podName, namespace, node, null, client);
                    throw new IllegalStateException("Pod '" + podName + "' is terminated. Status: " + status);
                }

                containerStatuses = pod.getStatus().getContainerStatuses();
                List<ContainerStatus> terminatedContainers = new ArrayList<>();
                for (ContainerStatus info : containerStatuses) {
                    if (info != null) {
                        if (info.getState().getTerminated() != null) {
                            // Container has errored
                            LOGGER.log(INFO, "Container is terminated {0} [{2}]: {1}",
                                    new Object[] { podName, info.getState().getTerminated(), info.getName() });
                            listener.getLogger().printf("Container is terminated %1$s [%3$s]: %2$s%n", podName,
                                    info.getState().getTerminated(), info.getName());
                            Metrics.metricRegistry().counter(MetricNames.LAUNCH_FAILED).inc();
                            terminatedContainers.add(info);
                        }
                    }
                }

                checkTerminatedContainers(terminatedContainers, podName, namespace, node, client);

                if (lastReportTimestamp + REPORT_INTERVAL < System.currentTimeMillis()) {
                    LOGGER.log(INFO, "Waiting for agent to connect ({1}/{2}): {0}",
                            new Object[]{podName, waitedForSlave, waitForSlaveToConnect});
                    listener.getLogger().printf("Waiting for agent to connect (%2$s/%3$s): %1$s%n", podName, waitedForSlave,
                            waitForSlaveToConnect);
                    lastReportTimestamp = System.currentTimeMillis();
                }
                Thread.sleep(1000);
            }
            if (slaveComputer == null || slaveComputer.isOffline()) {
                Metrics.metricRegistry().counter(MetricNames.LAUNCH_FAILED).inc();
                Metrics.metricRegistry().counter(MetricNames.FAILED_TIMEOUT).inc();

                logLastLines(containerStatuses, podName, namespace, node, null, client);
                throw new IllegalStateException(
                        "Agent is not connected after " + waitedForSlave + " seconds, status: " + status);
            }

            computer.setAcceptingTasks(true);
            launched = true;
            try {
                // We need to persist the "launched" setting...
                node.save();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
            }
            Metrics.metricRegistry().counter(MetricNames.PODS_LAUNCHED).inc();
        } catch (Throwable ex) {
            setProblem(ex);
            LOGGER.log(Level.WARNING, String.format("Error in provisioning; agent=%s, template=%s", node, template), ex);
            LOGGER.log(Level.FINER, "Removing Jenkins node: {0}", node.getNodeName());
            try {
                node.terminate();
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            }
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
        if (computer == null) {
            return;
        }

        final String podName = computer.getDisplayName();
        LOGGER.info("Agent " + podName + " disconnected");
        final OfflineCause cause = computer.getOfflineCause();

        for (Executor executor : computer.getExecutors()) {
            Queue.Executable executable = executor.getCurrentExecutable();
            if (executable == null) {
                continue;
            }

            executor.interrupt(Result.ABORTED, new PodTerminatedCause(podName, cause));

            final Queue.Task task = executable.getParent().getOwnerTask();
            LOGGER.warning("Aborted " + task.getName() + " because its pod agent was terminated");
        }

        super.afterDisconnect(computer, listener);
    }

    private void checkTerminatedContainers(List<ContainerStatus> terminatedContainers, String podId, String namespace,
                                           KubernetesSlave slave, KubernetesClient client) {
        if (!terminatedContainers.isEmpty()) {
            Map<String, Integer> errors = terminatedContainers.stream().collect(Collectors
                    .toMap(ContainerStatus::getName, (info) -> info.getState().getTerminated().getExitCode()));

            // Print the last lines of failed containers
            logLastLines(terminatedContainers, podId, namespace, slave, errors, client);
            throw new IllegalStateException("Containers are terminated with exit codes: " + errors);
        }
    }

    /**
     * Log the last lines of containers logs
     */
    private void logLastLines(@CheckForNull List<ContainerStatus> containers, String podId, String namespace, KubernetesSlave slave,
            Map<String, Integer> errors, KubernetesClient client) {
        if (containers != null) {
            for (ContainerStatus containerStatus : containers) {
                String containerName = containerStatus.getName();
                String log = client.pods().inNamespace(namespace).withName(podId)
                        .inContainer(containerStatus.getName()).tailingLines(30).getLog();
                if (!StringUtils.isBlank(log)) {
                    String msg = errors != null ? String.format(" exited with error %s", errors.get(containerName)) : "";
                    LOGGER.log(Level.SEVERE, "Error in provisioning; agent={0}, template={1}. Container {2}{3}. Logs: {4}",
                            new Object[]{slave, slave.getTemplateOrNull(), containerName, msg, log});
                }
            }
        }
    }

    /**
     * The last problem that occurred, if any.
     * @return
     */
    @CheckForNull
    public Throwable getProblem() {
        return problem;
    }

    public void setProblem(@CheckForNull Throwable problem) {
        this.problem = problem;
    }

}
