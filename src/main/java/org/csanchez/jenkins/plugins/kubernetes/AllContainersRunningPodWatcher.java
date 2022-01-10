package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.stream.Collectors.joining;

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
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.utils.Serialization;

/**
 * A pod watcher reporting when all containers are running
 */
public class AllContainersRunningPodWatcher implements Watcher<Pod> {
    private static final Logger LOGGER = Logger.getLogger(AllContainersRunningPodWatcher.class.getName());

    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Pod> reference = new AtomicReference<>();

    private Pod pod;

    private KubernetesClient client;

    public AllContainersRunningPodWatcher(KubernetesClient client, Pod pod) {
        this.client = client;
        this.pod = pod;
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
        return pod.getSpec().getContainers().size() == pod.getStatus().getContainerStatuses().size() && PodUtils.getContainerStatus(pod).stream().allMatch(ContainerStatus::getReady);
    }

    @Override
    public void onClose(WatcherException cause) {

    }

    /**
     * Wait until all pod containers are running
     * 
     * @return the pod
     * @throws PodNotRunningException
     *             if pod or containers are no longer running
     * @throws KubernetesClientTimeoutException
     *             if time ran out
     */
    public Pod await(long amount, TimeUnit timeUnit) throws PodNotRunningException {
        long started = System.currentTimeMillis();
        long alreadySpent = System.currentTimeMillis() - started;
        long remaining = timeUnit.toMillis(amount) - alreadySpent;
        if (remaining <= 0) {
            return periodicAwait(0, System.currentTimeMillis(), 0, 0);
        }
        try {
            long interval = Math.min(10000L, Math.max(remaining / 10, 1000L));
            long retries = remaining / interval;
            return periodicAwait(retries, System.currentTimeMillis(), interval, remaining);
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
     * @throws PodNotRunningException
     *             if pod or containers are no longer running
     * @throws KubernetesClientTimeoutException
     *             if time ran out
     */
    private Pod periodicAwait(long i, long started, long interval, long amount) throws PodNotRunningException {
        Pod pod = client.pods().inNamespace(this.pod.getMetadata().getNamespace())
                .withName(this.pod.getMetadata().getName()).get();
        if (pod == null) {
            throw new PodNotRunningException(String.format("Pod is no longer available: %s/%s",
                    this.pod.getMetadata().getNamespace(), this.pod.getMetadata().getName()));
        } else {
            LOGGER.finest(() -> "Updating pod for " + this.pod.getMetadata().getNamespace() + "/" + this.pod.getMetadata().getName() + " : " + Serialization.asYaml(pod));
            this.pod = pod;
        }
        List<ContainerStatus> terminatedContainers = PodUtils.getTerminatedContainers(pod);
        if (!terminatedContainers.isEmpty()) {
            PodNotRunningException x = new PodNotRunningException(String.format("Pod has terminated containers: %s/%s (%s)",
                    this.pod.getMetadata().getNamespace(),
                    this.pod.getMetadata().getName(),
                    terminatedContainers.stream()
                            .map(ContainerStatus::getName)
                            .collect(joining(", ")
                            )));
            String logs = PodUtils.logLastLines(this.pod, client);
            if (logs != null) {
                x.addSuppressed(new ContainerLogs(logs));
            }
            throw x;
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

    /** @see #await */
    public static final class PodNotRunningException extends Exception {
        PodNotRunningException(String s) {
            super(s);
        }
    }

}
