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


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Queue;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Launches on Kubernetes the specified {@link KubernetesComputer} instance.
 */
public class KubernetesLauncher extends JNLPLauncher {
    // Report progress every 30 seconds
    private static final long REPORT_INTERVAL = TimeUnit.SECONDS.toMillis(30L);

    @CheckForNull
    private transient AllContainersRunningPodWatcher watcher;

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
        KubernetesComputer kubernetesComputer = (KubernetesComputer) computer;
        computer.setAcceptingTasks(false);
        KubernetesSlave slave = kubernetesComputer.getNode();
        if (slave == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }
        if (launched) {
            LOGGER.log(INFO, "Agent has already been launched, activating: {0}", slave.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        final PodTemplate template = slave.getTemplate();
        try {
            KubernetesClient client = slave.getKubernetesCloud().connect();
            Pod pod = template.build(slave);
            slave.assignPod(pod);

            String podName = pod.getMetadata().getName();

            String namespace = Arrays.asList( //
                    pod.getMetadata().getNamespace(),
                    template.getNamespace(), client.getNamespace()) //
                    .stream().filter(s -> StringUtils.isNotBlank(s)).findFirst().orElse(null);
            slave.setNamespace(namespace);


            TaskListener runListener = template.getListener();

            LOGGER.log(FINE, "Creating Pod: {0}/{1}", new Object[] { namespace, podName });
            try {
                pod = client.pods().inNamespace(namespace).create(pod);
            } catch (KubernetesClientException e) {
                int k8sCode = e.getCode();
                if (k8sCode >= 400 && k8sCode < 500) { // 4xx
                    runListener.getLogger().printf("ERROR: Unable to create pod. " + e.getMessage());
                    PodUtils.cancelQueueItemFor(pod, e.getMessage());
                } else if (k8sCode >= 500 && k8sCode < 600) { // 5xx
                    LOGGER.log(FINE,"Kubernetes code {0}. Retrying...", e.getCode());
                } else {
                    LOGGER.log(WARNING, "Unknown Kubernetes code {0}", e.getCode());
                }
                throw e;
            }
            LOGGER.log(INFO, "Created Pod: {0}/{1}", new Object[] { namespace, podName });
            listener.getLogger().printf("Created Pod: %s/%s%n", namespace, podName);

            runListener.getLogger().printf("Created Pod: %s/%s%n", namespace, podName);

            template.getWorkspaceVolume().createVolume(client, pod.getMetadata());
            watcher = new AllContainersRunningPodWatcher(client, pod, runListener);
            try (Watch w1 = client.pods().inNamespace(namespace).withName(podName).watch(watcher);
                 Watch w2 = eventWatch(client, podName, namespace, runListener)) {
                assert watcher != null; // assigned 3 lines above
                watcher.await(template.getSlaveConnectTimeout(), TimeUnit.SECONDS);
            }
            LOGGER.log(INFO, "Pod is running: {0}/{1}", new Object[] { namespace, podName });

            // We need the pod to be running and connected before returning
            // otherwise this method keeps being called multiple times
            List<String> validStates = ImmutableList.of("Running");

            int waitForSlaveToConnect = template.getSlaveConnectTimeout();
            int waitedForSlave;

            // now wait for agent to be online
            SlaveComputer slaveComputer = null;
            String status = null;
            List<ContainerStatus> containerStatuses = null;
            long lastReportTimestamp = System.currentTimeMillis();
            for (waitedForSlave = 0; waitedForSlave < waitForSlaveToConnect; waitedForSlave++) {
                slaveComputer = slave.getComputer();
                if (slaveComputer == null) {
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (slaveComputer.isOnline()) {
                    break;
                }

                // Check that the pod hasn't failed already
                pod = client.pods().inNamespace(namespace).withName(podName).get();
                if (pod == null) {
                    throw new IllegalStateException("Pod no longer exists: " + podName);
                }
                status = pod.getStatus().getPhase();
                if (!validStates.contains(status)) {
                    break;
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
                            terminatedContainers.add(info);
                        }
                    }
                }

                checkTerminatedContainers(terminatedContainers, podName, namespace, slave, client);

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
                logLastLines(containerStatuses, podName, namespace, slave, null, client);
                throw new IllegalStateException(
                        "Agent is not connected after " + waitedForSlave + " seconds, status: " + status);
            }

            computer.setAcceptingTasks(true);
            launched = true;
            try {
                // We need to persist the "launched" setting...
                slave.save();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
            }
        } catch (Throwable ex) {
            setProblem(ex);
            LOGGER.log(Level.WARNING, String.format("Error in provisioning; agent=%s, template=%s", slave, template), ex);
            LOGGER.log(Level.FINER, "Removing Jenkins node: {0}", slave.getNodeName());
            try {
                slave.terminate();
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            }
            throw Throwables.propagate(ex);
        }
    }

    private Watch eventWatch(KubernetesClient client, String podName, String namespace, TaskListener runListener) {
        try {
            return client.events().inNamespace(namespace).withField("involvedObject.name", podName).watch(new TaskListenerEventWatcher(podName, runListener));
        } catch (KubernetesClientException e) {
            LOGGER.log(Level.INFO, e, () -> "Cannot watch events on " + namespace + "/" +podName);
        }
        return () -> {};
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
                PrettyLoggable<String, LogWatch> tailingLines = client.pods().inNamespace(namespace).withName(podId)
                        .inContainer(containerStatus.getName()).tailingLines(30);
                String log = tailingLines.getLog();
                if (!StringUtils.isBlank(log)) {
                    String msg = errors != null ? String.format(" exited with error %s", errors.get(containerName)) : "";
                    LOGGER.log(Level.SEVERE, "Error in provisioning; agent={0}, template={1}. Container {2}{3}. Logs: {4}",
                            new Object[]{slave, slave.getTemplate(), containerName, msg, tailingLines.getLog()});
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

    public AllContainersRunningPodWatcher getWatcher() {
        return watcher;
    }

}
