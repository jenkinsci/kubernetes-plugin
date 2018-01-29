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

import static java.util.logging.Level.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;

/**
 * Launches on Kubernetes the specified {@link KubernetesComputer} instance.
 */
public class KubernetesLauncher extends JNLPLauncher {

    private static final Logger LOGGER = Logger.getLogger(KubernetesLauncher.class.getName());

    private boolean launched;

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
    public void launch(SlaveComputer computer, TaskListener listener) {
        PrintStream logger = listener.getLogger();

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
            LOGGER.log(INFO, "Agent has already been launched, activating: {}", slave.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        KubernetesCloud cloud = slave.getKubernetesCloud();
        final PodTemplate unwrappedTemplate = slave.getTemplate();
        try {
            KubernetesClient client = cloud.connect();
            Pod pod = getPodTemplate(slave, unwrappedTemplate);

            String podId = pod.getMetadata().getName();
            String namespace = StringUtils.defaultIfBlank(slave.getNamespace(), client.getNamespace());

            LOGGER.log(Level.FINE, "Creating Pod: {0} in namespace {1}", new Object[]{podId, namespace});
            pod = client.pods().inNamespace(namespace).create(pod);
            LOGGER.log(INFO, "Created Pod: {0} in namespace {1}", new Object[]{podId, namespace});
            logger.printf("Created Pod: %s in namespace %s%n", podId, namespace);

            // We need the pod to be running and connected before returning
            // otherwise this method keeps being called multiple times
            List<String> validStates = ImmutableList.of("Running");

            int i = 0;
            int j = 100; // wait 600 seconds

            List<ContainerStatus> containerStatuses = null;

            // wait for Pod to be running
            for (; i < j; i++) {
                LOGGER.log(INFO, "Waiting for Pod to be scheduled ({1}/{2}): {0}", new Object[]{podId, i, j});
                logger.printf("Waiting for Pod to be scheduled (%2$s/%3$s): %1$s%n", podId, i, j);

                Thread.sleep(6000);
                pod = client.pods().inNamespace(namespace).withName(podId).get();
                if (pod == null) {
                    throw new IllegalStateException("Pod no longer exists: " + podId);
                }

                containerStatuses = pod.getStatus().getContainerStatuses();
                List<ContainerStatus> terminatedContainers = new ArrayList<>();
                Boolean allContainersAreReady = true;
                for (ContainerStatus info : containerStatuses) {
                    if (info != null) {
                        if (info.getState().getWaiting() != null) {
                            // Pod is waiting for some reason
                            LOGGER.log(INFO, "Container is waiting {0} [{2}]: {1}",
                                    new Object[]{podId, info.getState().getWaiting(), info.getName()});
                            logger.printf("Container is waiting %1$s [%3$s]: %2$s%n",
                                    podId, info.getState().getWaiting(), info.getName());
                            // break;
                        }
                        if (info.getState().getTerminated() != null) {
                            terminatedContainers.add(info);
                        } else if (!info.getReady()) {
                            allContainersAreReady = false;
                        }
                    }
                }

                if (!terminatedContainers.isEmpty()) {
                    Map<String, Integer> errors = terminatedContainers.stream().collect(Collectors
                            .toMap(ContainerStatus::getName, (info) -> info.getState().getTerminated().getExitCode()));

                    // Print the last lines of failed containers
                    logLastLines(terminatedContainers, podId, namespace, slave, errors, client);
                    throw new IllegalStateException("Containers are terminated with exit codes: " + errors);
                }

                if (!allContainersAreReady) {
                    continue;
                }

                if (validStates.contains(pod.getStatus().getPhase())) {
                    break;
                }
            }
            String status = pod.getStatus().getPhase();
            if (!validStates.contains(status)) {
                throw new IllegalStateException("Container is not running after " + j + " attempts, status: " + status);
            }

            j = unwrappedTemplate.getSlaveConnectTimeout();

            // now wait for agent to be online
            for (; i < j; i++) {
                if (slave.getComputer() == null) {
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (slave.getComputer().isOnline()) {
                    break;
                }
                LOGGER.log(INFO, "Waiting for agent to connect ({1}/{2}): {0}", new Object[]{podId, i, j});
                logger.printf("Waiting for agent to connect (%2$s/%3$s): %1$s%n", podId, i, j);
                Thread.sleep(1000);
            }
            if (!slave.getComputer().isOnline()) {
                if (containerStatuses != null) {
                    logLastLines(containerStatuses, podId, namespace, slave, null, client);
                }
                throw new IllegalStateException("Agent is not connected after " + j + " attempts, status: " + status);
            }
            computer.setAcceptingTasks(true);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, String.format("Error in provisioning; agent=%s, template=%s", slave, unwrappedTemplate), ex);
            LOGGER.log(Level.FINER, "Removing Jenkins node: {0}", slave.getNodeName());
            try {
                slave.terminate();
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            }
            throw Throwables.propagate(ex);
        }
        launched = true;
        try {
            // We need to persist the "launched" setting...
            slave.save();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
        }
    }

    private Pod getPodTemplate(KubernetesSlave slave, PodTemplate template) {
        return template == null ? null : template.build(slave);
    }

    /**
     * Log the last lines of containers logs
     */
    private void logLastLines(List<ContainerStatus> containers, String podId, String namespace, KubernetesSlave slave,
                              Map<String, Integer> errors, KubernetesClient client) {
        for (ContainerStatus containerStatus : containers) {
            String containerName = containerStatus.getName();
            PrettyLoggable<String, LogWatch> tailingLines = client.pods().inNamespace(namespace)
                    .withName(podId).inContainer(containerStatus.getName()).tailingLines(30);
            String log = tailingLines.getLog();
            if (!StringUtils.isBlank(log)) {
                String msg = errors != null ? String.format(" exited with error %s", errors.get(containerName))
                        : "";
                LOGGER.log(Level.SEVERE,
                        "Error in provisioning; agent={0}, template={1}. Container {2}{3}. Logs: {4}",
                        new Object[]{slave, slave.getTemplate(), containerName, msg, tailingLines.getLog()});
            }
        }
    }

}
