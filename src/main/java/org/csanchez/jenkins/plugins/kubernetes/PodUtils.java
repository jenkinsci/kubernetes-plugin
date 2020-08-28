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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Queue;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
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

    public static void cancelInvalidPodTemplateJob(Pod pod, String reason) {
        Queue q = Jenkins.get().getQueue();
        String runUrl = pod.getMetadata().getAnnotations().get("runUrl");
        for (Queue.Item item: q.getItems()) {
            if (item.task.getUrl().equals(runUrl)) {
                String cancelMsg = "Canceling queue item: " + item;
                if (reason != null && !StringUtils.isBlank(reason)) {
                    cancelMsg += " due to " + reason;
                }
                LOGGER.info(cancelMsg);
                q.cancel(item);
                break;
            }
        }
        LOGGER.info("Failed to find corresponding queue item to cancel for pod: " + pod);
    }
}
