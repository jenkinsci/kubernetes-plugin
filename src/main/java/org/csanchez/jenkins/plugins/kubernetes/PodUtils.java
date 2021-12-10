/*
 * Copyright 2020 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Queue;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Map;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class PodUtils {
    private static final Logger LOGGER = Logger.getLogger(PodUtils.class.getName());

    public static final Predicate<ContainerStatus> CONTAINER_IS_TERMINATED = cs -> cs.getState().getTerminated() != null;
    public static final Predicate<ContainerStatus> CONTAINER_IS_WAITING = cs -> cs.getState().getWaiting() != null;

    @NonNull
    public static List<ContainerStatus> getTerminatedContainers(Pod pod) {
        return getContainers(pod, CONTAINER_IS_TERMINATED);
    }

    public static List<ContainerStatus> getWaitingContainers(Pod pod) {
        return getContainers(pod, CONTAINER_IS_WAITING);
    }

    public static List<ContainerStatus> getContainerStatus(Pod pod) {
        PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return Collections.emptyList();
        }
        return podStatus.getContainerStatuses();
    }

    public static List<ContainerStatus> getContainers(Pod pod, Predicate<ContainerStatus> predicate) {
        return getContainerStatus(pod).stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * Cancel queue items matching the given pod.
     * It uses the annotation "runUrl" added to the pod to do the matching.
     *
     * It uses the current thread context to list item queues,
     * so make sure to be in the right context before calling this method.
     *
     * @param pod The pod to cancel items for.
     * @param reason The reason the item are being cancelled.
     */
    public static void cancelQueueItemFor(Pod pod, String reason) {
        Queue q = Jenkins.get().getQueue();
        boolean cancelled = false;
        ObjectMeta metadata = pod.getMetadata();
        if (metadata == null) {
            return;
        }
        Map<String, String> annotations = metadata.getAnnotations();
        if (annotations == null) {
            LOGGER.log(Level.FINE, "Pod .metadata.annotations is null: {0}/{1}", new Object[] {metadata.getNamespace(), metadata.getName()});
            return;
        }
        String runUrl = annotations.get("runUrl");
        if (runUrl == null) {
            LOGGER.log(Level.FINE, "Pod .metadata.annotations.runUrl is null: {0}/{1}", new Object[] {metadata.getNamespace(), metadata.getName()});
            return;
        }
        for (Queue.Item item: q.getItems()) {
            Queue.Task task = item.task;
            if (runUrl.equals(task.getUrl())) {
                LOGGER.log(Level.FINE, "Cancelling queue item: \"{0}\"\n{1}",
                        new Object[]{ task.getDisplayName(), !StringUtils.isBlank(reason) ? "due to " + reason : ""});
                q.cancel(item);
                cancelled = true;
                break;
            }
        }
        if (!cancelled) {
            LOGGER.log(Level.FINE, "No queue item found for pod: {0}/{1}", new Object[] {metadata.getNamespace(), metadata.getName()});
        }
    }

    @CheckForNull
    public static String logLastLines(@NonNull Pod pod, @NonNull KubernetesClient client) {
        PodStatus status = pod.getStatus();
        ObjectMeta metadata = pod.getMetadata();
        if (status == null || metadata == null) {
             return null;
        }
        String podName = metadata.getName();
        String namespace = metadata.getNamespace();
        List<ContainerStatus> containers = status.getContainerStatuses();
        StringBuilder sb = new StringBuilder();
        if (containers != null) {
            for (ContainerStatus containerStatus : containers) {
                sb.append("\n");
                sb.append("- ");
                sb.append(containerStatus.getName());
                if (containerStatus.getState().getTerminated() != null) {
                    sb.append(" -- terminated (");
                    sb.append(containerStatus.getState().getTerminated().getExitCode());
                    sb.append(")");
                }
                if (containerStatus.getState().getRunning() != null) {
                    sb.append(" -- running");
                }
                if (containerStatus.getState().getWaiting() != null) {
                    sb.append(" -- waiting");
                }
                sb.append("\n");
                try {
                    String log = client.pods()
                            .inNamespace(namespace)
                            .withName(podName)
                            .inContainer(containerStatus.getName())
                            .tailingLines(30)
                            .getLog();
                    sb.append("-----Logs-------------\n");
                    sb.append(log);
                    sb.append("\n");
                } catch (KubernetesClientException e) {
                    LOGGER.log(Level.FINE, "Unable to retrieve container logs as it is already gone", e);
                }
            }
        }
        return Util.fixEmpty(sb.toString());
    }
}
