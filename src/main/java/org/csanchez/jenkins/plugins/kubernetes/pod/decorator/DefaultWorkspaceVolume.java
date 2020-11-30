package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import hudson.Extension;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Sets a workspace volume mounted in all containers.
 */
@Extension
public class DefaultWorkspaceVolume implements PodDecorator {
    private static final String WORKSPACE_VOLUME_NAME = "workspace-volume";
    public static final Predicate<Volume> WORKSPACE_VOLUME_PREDICATE = v -> WORKSPACE_VOLUME_NAME.equals(v.getName());

    private static final Volume DEFAULT_WORKSPACE_VOLUME = new VolumeBuilder()
            .withName(WORKSPACE_VOLUME_NAME)
            .withNewEmptyDir().endEmptyDir()
            .build();

    private static final VolumeMount DEFAULT_WORKSPACE_VOLUME_MOUNT = new VolumeMountBuilder()
            .withName(WORKSPACE_VOLUME_NAME)
            .withReadOnly(false)
            .build();

    @Nonnull
    @Override
    public Pod decorate(@Nonnull KubernetesCloud kubernetesCloud, @Nonnull Pod pod) { PodSpec podSpec = pod.getSpec();
        // default workspace volume, add an empty volume to share the workspace across the pod
        if (podSpec.getVolumes().stream().noneMatch(WORKSPACE_VOLUME_PREDICATE)) {
            // @formatter:off
            pod = new PodBuilder(pod)
                    .editSpec()
                        .addNewVolumeLike(DEFAULT_WORKSPACE_VOLUME).endVolume()
                    .endSpec()
                    .build();
            // @formatter:on
        }
        // default workspace volume mount. If something is already mounted in the same path ignore it
        pod.getSpec().getContainers().stream()
                .filter(c -> c.getVolumeMounts().stream()
                        .noneMatch(vm -> vm.getMountPath().equals(
                                getWorkingDir(c))))
                .forEach(c -> {
                    List<VolumeMount> volumeMounts = c.getVolumeMounts() == null ? new ArrayList<>() : c.getVolumeMounts();
                    volumeMounts.add(new VolumeMountBuilder(DEFAULT_WORKSPACE_VOLUME_MOUNT)
                            .withMountPath(getWorkingDir(c))
                            .build()
                    );
                    c.setVolumeMounts(volumeMounts);
                });
        return pod;
    }

    private String getWorkingDir(Container c) {
        return c.getWorkingDir() != null ? c.getWorkingDir() : ContainerTemplate.DEFAULT_WORKING_DIR;
    }

}
