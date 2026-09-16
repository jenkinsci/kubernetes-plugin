package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Pod container sources are responsible to locating details about Pod containers.
 */
public abstract class PodContainerSource implements ExtensionPoint {

    /**
     * Lookup the working directory of the named container.
     * @param pod pod reference to lookup container in
     * @param containerName name of container to lookup
     * @return working directory path if container found and working dir specified, otherwise empty
     */
    public abstract Optional<String> getContainerWorkingDir(@NonNull Pod pod, @NonNull String containerName);

    /**
     * Lookup the status of the named container.
     * @param pod pod reference to lookup container in
     * @param containerName name of container to lookup
     * @return container status if found, otherwise empty
     */
    public abstract Optional<ContainerStatus> getContainerStatus(@NonNull Pod pod, @NonNull String containerName);

    /**
     * Lookup all {@link PodContainerSource} extensions.
     * @return pod container source extension list
     */
    @NonNull
    public static List<PodContainerSource> all() {
        return ExtensionList.lookup(PodContainerSource.class);
    }

    /**
     * Lookup pod container working dir. Searches all {@link PodContainerSource} extensions and returns
     * the first non-empty result.
     * @param pod pod to inspect
     * @param containerName container to search for
     * @return optional working dir if container found and working dir, possibly empty
     */
    public static Optional<String> lookupContainerWorkingDir(@NonNull Pod pod, @NonNull String containerName) {
        return all().stream()
                .map(cs -> cs.getContainerWorkingDir(pod, containerName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Lookup container status (either main container or ephemeral container).
     * @param pod pod resource to inspect
     * @param containerName container to locate
     * @return container status if found
     */
    public static Optional<ContainerStatus> lookupContainerStatus(Pod pod, String containerName) {
        return PodContainerSource.all().stream()
                .map(cs -> cs.getContainerStatus(pod, containerName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * A container with {@code restartPolicy: Always} in {@code initContainers} is a
     * <a href="https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/">native
     * sidecar</a> (Kubernetes 1.29+): it keeps running for the lifetime of the pod, just like a
     * regular container, so it is a valid target for the {@code container()} step. A plain
     * (run-to-completion) init container is not, since it will already have terminated by the
     * time any pipeline step runs.
     */
    private static final String NATIVE_SIDECAR_RESTART_POLICY = "Always";

    /**
     * Default implementation of {@link PodContainerSource} that searches the primary pod
     * containers, plus any {@code initContainers} entry declared as a native sidecar
     * ({@code restartPolicy: Always}). Plain (run-to-completion) init containers and ephemeral
     * containers are not included in container lookups in this implementation.
     * @see PodSpec#getContainers()
     * @see PodSpec#getInitContainers()
     */
    @Extension
    public static final class DefaultPodContainerSource extends PodContainerSource {

        @Override
        public Optional<String> getContainerWorkingDir(@NonNull Pod pod, @NonNull String containerName) {
            Optional<String> workingDir = pod.getSpec().getContainers().stream()
                    .filter(c -> Objects.equals(c.getName(), containerName))
                    .findAny()
                    .map(Container::getWorkingDir);
            if (workingDir.isPresent()) {
                return workingDir;
            }
            return nativeSidecars(pod)
                    .filter(c -> Objects.equals(c.getName(), containerName))
                    .findAny()
                    .map(Container::getWorkingDir);
        }

        @Override
        public Optional<ContainerStatus> getContainerStatus(@NonNull Pod pod, @NonNull String containerName) {
            PodStatus podStatus = pod.getStatus();
            if (podStatus == null) {
                return Optional.empty();
            }

            Optional<ContainerStatus> status = podStatus.getContainerStatuses().stream()
                    .filter(cs -> Objects.equals(cs.getName(), containerName))
                    .findFirst();
            if (status.isPresent()) {
                return status;
            }
            List<ContainerStatus> initContainerStatuses = podStatus.getInitContainerStatuses();
            if (initContainerStatuses == null) {
                return Optional.empty();
            }
            return initContainerStatuses.stream()
                    .filter(cs -> Objects.equals(cs.getName(), containerName))
                    .filter(cs -> nativeSidecars(pod).anyMatch(c -> Objects.equals(c.getName(), cs.getName())))
                    .findFirst();
        }

        private static Stream<Container> nativeSidecars(@NonNull Pod pod) {
            List<Container> initContainers = pod.getSpec().getInitContainers();
            if (initContainers == null) {
                return Stream.empty();
            }
            return initContainers.stream().filter(c -> NATIVE_SIDECAR_RESTART_POLICY.equals(c.getRestartPolicy()));
        }
    }
}
