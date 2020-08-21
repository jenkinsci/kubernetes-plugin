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

import com.ctc.wstx.util.StringUtil;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Queue;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import jenkins.model.Jenkins;

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
        return getContainerStatus(pod, false);
    }

    public static List<ContainerStatus> getContainerStatus(Pod pod, boolean checkForError) throws InvalidPodTemplateException {
        PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return Collections.emptyList();
        }
        List<ContainerStatus> csList = podStatus.getContainerStatuses();
        if (checkForError) {
            checkContainersForInvalidPodTemplate(csList);
        }
        return csList;
    }

    public static List<ContainerStatus> getContainers(Pod pod, Predicate<ContainerStatus> predicate) {
        return getContainerStatus(pod).stream().filter(predicate).collect(Collectors.toList());
    }

    /* currently checks for ImagePullBackOff error */
    public static void checkContainersForInvalidPodTemplate(List<ContainerStatus> containerStatusList) throws InvalidPodTemplateException {
        for (ContainerStatus cs : containerStatusList) {
            ContainerStateWaiting waiting = cs.getState().getWaiting();
            if (waiting != null && "ImagePullBackOff".equals(waiting.getReason())) {
                throw new InvalidPodTemplateException(waiting.getReason(), waiting.getMessage());
            }
        }
    }

    public static void cancelInvalidPodTemplateJob(Pod pod) {
        cancelInvalidPodTemplateJob(pod, null);
    }

    public static void cancelInvalidPodTemplateJob(Pod pod, String reason) {
        Queue q = Jenkins.get().getQueue();
        String runUrl = pod.getMetadata().getAnnotations().get("runUrl");
        for (Queue.Item item: q.getItems()) {
            if (item.task.getUrl().equals(runUrl)) {
                String cancelMsg = "Canceling queue item: " + item;
                if (reason != null && !StringUtil.isAllWhitespace(reason)) {
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
