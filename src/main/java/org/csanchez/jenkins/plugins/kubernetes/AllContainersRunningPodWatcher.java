package org.csanchez.jenkins.plugins.kubernetes;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.Watcher;

/**
 * A pod watcher reporting when all containers are running
 */
public class AllContainersRunningPodWatcher implements Watcher<Pod> {
    private static final Logger LOGGER = Logger.getLogger(AllContainersRunningPodWatcher.class.getName());

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Pod> reference = new AtomicReference<>();

    private Pod pod;

    private KubernetesClient client;

    private PodStatus podStatus;

    public AllContainersRunningPodWatcher(KubernetesClient client, Pod pod) {
        this.client = client;
        this.pod = pod;
        this.podStatus = pod.getStatus();
        if (areAllContainersRunning(pod)) {
            reference.set(pod);
            latch.countDown();
        }
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
        LOGGER.log(Level.FINEST, "[{0}] {1}", new Object[]{action, pod.getMetadata().getName()});
        this.podStatus = pod.getStatus();
        switch (action) {
            case MODIFIED:
                if (areAllContainersRunning(pod)) {
                    LOGGER.log(Level.FINE, "All containers are running for pod {0}", new Object[] {pod.getMetadata().getName()});
                    reference.set(pod);
                    latch.countDown();
                }
                break;
            default:
        }
    }

    boolean areAllContainersRunning(Pod pod) {
        boolean allContainersAreReady = true;
        PodStatus podStatus = pod.getStatus();
        if (podStatus == null) {
            return false;
        }
        List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
        if (containerStatuses.isEmpty()) {
            allContainersAreReady = false;
        }
        for (ContainerStatus containerStatus : containerStatuses) {
            if (containerStatus != null) {
                if (containerStatus.getState().getWaiting() != null) {
                    allContainersAreReady = false;
                }
                if (containerStatus.getState().getTerminated() != null) {
                    allContainersAreReady = false;
                }
                if (!containerStatus.getReady()) {
                    allContainersAreReady = false;
                }
            }
        }
        return allContainersAreReady;
    }

    @Override
    public void onClose(KubernetesClientException cause) {

    }

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

    private Pod periodicAwait(int i, long started, long interval, long amount) {
        Pod pod = client.pods().inNamespace(this.pod.getMetadata().getNamespace())
                .withName(this.pod.getMetadata().getName()).get();
        if (pod == null) {
            throw new IllegalStateException(String.format("Pod is no longer available: %s/%s",
                    this.pod.getMetadata().getNamespace(), this.pod.getMetadata().getName()));
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
        return podStatus;
    }
}
