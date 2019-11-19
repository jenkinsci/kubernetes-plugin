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

import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.Watcher;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * A pod watcher reporting when all containers are running
 */
public class AllContainersRunningPodWatcher implements Watcher<Pod> {
    private static final Logger LOGGER = Logger.getLogger(AllContainersRunningPodWatcher.class.getName());

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Pod> reference = new AtomicReference<>();

    private Pod pod;

    private KubernetesClient client;

    @Nonnull
    private final TaskListener runListener;

    public AllContainersRunningPodWatcher(KubernetesClient client, Pod pod, @CheckForNull TaskListener runListener) {
        this.client = client;
        this.pod = pod;
        this.runListener = runListener == null ? TaskListener.NULL : runListener;
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
                        runListener.error("Unable to pull Docker image \""+containerStatus.getImage()+"\". Check if image name is spelled correctly");
                        throw new IllegalStateException("BAD_DOCKER_IMAGE");
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
}
