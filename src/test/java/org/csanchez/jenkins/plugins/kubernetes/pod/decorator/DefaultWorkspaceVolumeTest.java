package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.WithoutJenkins;

class DefaultWorkspaceVolumeTest {

    private final DefaultWorkspaceVolume decorator = new DefaultWorkspaceVolume();
    private final KubernetesCloud cloud = new KubernetesCloud("test");

    @WithoutJenkins
    @Test
    void mountsWorkspaceVolumeInNativeSidecar() {
        Pod pod = new PodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName("jnlp")
                .endContainer()
                .addNewInitContainer()
                .withName("sidecar")
                .withRestartPolicy("Always")
                .endInitContainer()
                .addNewInitContainer()
                .withName("setup")
                .endInitContainer()
                .endSpec()
                .build();

        Pod decorated = decorator.decorate(cloud, pod);

        Container jnlp = findContainer(decorated.getSpec().getContainers(), "jnlp");
        assertHasWorkspaceVolumeMount(jnlp);

        Container sidecar = findContainer(decorated.getSpec().getInitContainers(), "sidecar");
        assertHasWorkspaceVolumeMount(sidecar);

        // plain (run-to-completion) init container does not need the shared workspace: it has
        // already terminated by the time any pipeline step could use it.
        Container setup = findContainer(decorated.getSpec().getInitContainers(), "setup");
        assertTrue(
                setup.getVolumeMounts() == null
                        || setup.getVolumeMounts().stream()
                                .noneMatch(vm -> vm.getName().equals(PodTemplateBuilder.WORKSPACE_VOLUME_NAME)),
                "plain init container should not get the workspace volume mount");
    }

    private static Container findContainer(List<Container> containers, String name) {
        return containers.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("container " + name + " not found"));
    }

    private static void assertHasWorkspaceVolumeMount(Container container) {
        VolumeMount mount = container.getVolumeMounts().stream()
                .filter(vm -> vm.getName().equals(PodTemplateBuilder.WORKSPACE_VOLUME_NAME))
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("container " + container.getName() + " missing workspace volume mount"));
        assertEquals(PodTemplateBuilder.WORKSPACE_VOLUME_NAME, mount.getName());
    }
}
