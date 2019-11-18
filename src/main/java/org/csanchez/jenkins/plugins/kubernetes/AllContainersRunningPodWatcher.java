package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.Watcher;
import jenkins.model.Jenkins;

/**
 * A pod watcher reporting when all containers are running
 */
public class AllContainersRunningPodWatcher implements Watcher<Pod> {
    private static final Logger LOGGER = Logger.getLogger(AllContainersRunningPodWatcher.class.getName());

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Pod> reference = new AtomicReference<>();

    private Pod pod;

    private KubernetesClient client;

    private boolean throwTimeoutError;

    @Nonnull
    private final TaskListener listener;

    public AllContainersRunningPodWatcher(KubernetesClient client, Pod pod, @CheckForNull TaskListener listener) {
        this.client = client;
        this.pod = pod;
        this.listener = listener == null ? TaskListener.NULL : listener;
        this.throwTimeoutError = false;
        updateState(pod);
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
        LOGGER.log(Level.FINEST, "[{0}] {1}", new Object[]{action, pod.getMetadata().getName()});
        switch (action) {
            case MODIFIED:
                updateState(pod);
                break;
            default:
        }
    }

    private void updateState(Pod pod) {
        if (areAllContainersRunning(pod)) {
            LOGGER.log(Level.FINE, "All containers are running for pod {0}", new Object[] {pod.getMetadata().getName()});
            reference.set(pod);
            latch.countDown();
        }
    }

    boolean areAllContainersRunning(Pod pod) {
        PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return false;
        }
        List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
        if (containerStatuses.isEmpty()) {
            return false;
        }
        for (ContainerStatus containerStatus : containerStatuses) {
            if (containerStatus != null) {
                ContainerStateWaiting waitingState = containerStatus.getState().getWaiting();
                if (waitingState != null) {
                    String waitingStateMsg = waitingState.getMessage();
                    if (waitingStateMsg != null && waitingStateMsg.contains("Back-off pulling image")) {
                        LOGGER.log(Level.INFO, "Unable to pull Docker image");
                        listener.error("Unable to pull Docker image \""+ containerStatus.getImage() +"\". Check if image name is spelled correctly..");
                        Jenkins jenkins = Jenkins.getInstanceOrNull();

                        if (jenkins != null) {
                            Queue q = jenkins.getQueue();

                            for (Queue.Item item : q.getItems()) {
                                Label itemLabel = item.getAssignedLabel();
                                // Check if the pod name starts with the item label + '-'
                                if (itemLabel != null && pod.getMetadata().getName().startsWith(itemLabel.getDisplayName() + "-")) {
                                    String itemTaskName = item.task.getFullDisplayName();
                                    String jobName = getJobName(itemTaskName);
                                    if (jobName.equals("")) {
                                        String msg = "Unknown / Invalid job format name";
                                        this.listener.getLogger().println(msg);
                                        LOGGER.log(Level.WARNING, msg);
                                        break;
                                    }
                                    String msg = "Cancelling the Queue..";
                                    this.listener.getLogger().println(msg);
                                    LOGGER.log(Level.WARNING, msg);
                                    q.cancel(item);
                                    // Stop the running timers and exit. this will avoid error building the next iteration of this job
                                    // if triggered before timeout running at periodicAwait function
                                    this.throwTimeoutError = true;
                                    break;
                                }
                            }
                        }
                    }
                    return false;
                }
                if (containerStatus.getState().getTerminated() != null) {
                    return false;
                }
                if (!containerStatus.getReady()) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<ContainerStatus> getTerminatedContainers(Pod pod) {
        PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return Collections.emptyList();
        }
        List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
        if (containerStatuses.isEmpty()) {
            return Collections.emptyList();
        }
        List<ContainerStatus> result = new ArrayList<>();
        for (ContainerStatus containerStatus : containerStatuses) {
            if (containerStatus != null) {
                if (containerStatus.getState().getTerminated() != null) {
                    result.add(containerStatus);
                }
            }
        }
        return result;
    }

    @Override
    public void onClose(KubernetesClientException cause) {

    }

    /**
     * Wait until all pod containers are running
     * 
     * @return the pod
     * @throws IllegalStateException
     *             if pod or containers are no longer running
     * @throws KubernetesClientTimeoutException
     *             if time ran out
     */
    public Pod await(long amount, TimeUnit timeUnit) {
        long started = System.currentTimeMillis();
        long alreadySpent = System.currentTimeMillis() - started;
        long remaining = timeUnit.toMillis(amount) - alreadySpent;
        if (remaining <= 0) {
            return periodicAwait(0, System.currentTimeMillis(), 0, 0);
        }
        try {
            return periodicAwait(10, System.currentTimeMillis(), Math.max(remaining / 10, 1000L), remaining);
        } catch (KubernetesClientTimeoutException e) {
            // Wrap using the right timeout
            throw new KubernetesClientTimeoutException(pod, amount, timeUnit);
        }
    }

    private Pod awaitWatcher(long amount, TimeUnit timeUnit) {
        try {
            if (latch.await(amount, timeUnit)) {
                return reference.get();
            }
            throw new KubernetesClientTimeoutException(pod, amount, timeUnit);
        } catch (InterruptedException e) {
            throw new KubernetesClientTimeoutException(pod, amount, timeUnit);
        }
    }

    /**
     * Wait until all pod containers are running
     * 
     * @return the pod
     * @throws IllegalStateException
     *             if pod or containers are no longer running
     * @throws KubernetesClientTimeoutException
     *             if time ran out
     */
    private Pod periodicAwait(int i, long started, long interval, long amount) {
        Pod pod = client.pods().inNamespace(this.pod.getMetadata().getNamespace())
                .withName(this.pod.getMetadata().getName()).get();
        if (pod == null) {
            throw new IllegalStateException(String.format("Pod is no longer available: %s/%s",
                    this.pod.getMetadata().getNamespace(), this.pod.getMetadata().getName()));
        } else {
            LOGGER.finest(() -> "Updating pod for " + this.pod.getMetadata().getNamespace() + "/" + this.pod.getMetadata().getName() + " : " + pod);
            this.pod = pod;
        }
        List<ContainerStatus> terminatedContainers = getTerminatedContainers(pod);
        if (!terminatedContainers.isEmpty()) {
            throw new IllegalStateException(String.format("Pod has terminated containers: %s/%s (%s)",
                    this.pod.getMetadata().getNamespace(),
                    this.pod.getMetadata().getName(),
                    terminatedContainers.stream()
                            .map(ContainerStatus::getName)
                            .collect(joining(", ")
                            )));
        }
        if (areAllContainersRunning(pod)) {
            return pod;
        }

        if(this.throwTimeoutError) {
            i = 0;  // Don't loop here anymore.
            throw new KubernetesClientTimeoutException(pod, interval, TimeUnit.MILLISECONDS);
        }


        try {
            return awaitWatcher(interval, TimeUnit.MILLISECONDS);
        } catch (KubernetesClientTimeoutException e) {
            if (i <= 0) {
                throw e;
            }
        }

        long remaining = (started + amount) - System.currentTimeMillis();
        long next = Math.max(0, Math.min(remaining, interval));
        return periodicAwait(i - 1, started, next, amount);
    }

    public PodStatus getPodStatus() {
        return this.pod.getStatus();
    }


    /*
     * itemTaskName is format of "part of <ORGANIZATION> <JOB NAME> >> <BRANCH>
     * #<BUILD NUMBER>
     */
    /*
     * <ORGANIZATION> and <BRANCH> are only there if Pipeline created through
     * BlueOcean, else just <JOB NAME>
     */
    private String getJobName(String itemTaskName) {
        final String partOfStr = "part of ";
        int begin = partOfStr.length();
        int end = itemTaskName.lastIndexOf(" #");
        if (end < 0)
            return "";
        return itemTaskName.substring(begin, end);
    }

}