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
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
     * Default implementation of {@link PodContainerSource} that only searches the primary
     * pod containers. Ephemeral or init containers are not included container lookups in
     * this implementation.
     * @see PodSpec#getContainers()
     */
    @Extension
    public static final class DefaultPodContainerSource extends PodContainerSource {

        @Override
        public Optional<String> getContainerWorkingDir(@NonNull Pod pod, @NonNull String containerName) {
            return pod.getSpec().getContainers().stream()
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

            return podStatus.getContainerStatuses().stream()
                    .filter(cs -> StringUtils.equals(cs.getName(), containerName))
                    .findFirst();
        }
    }
}
