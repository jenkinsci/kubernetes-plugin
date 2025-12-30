package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.jupiter.api.Assertions.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EphemeralContainer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PodContainerSourceTest {

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void lookupContainerWorkingDir() {
        Pod pod = new PodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName("foo")
                .withWorkingDir("/app/foo")
                .endContainer()
                .addNewEphemeralContainer()
                .withName("bar")
                .withWorkingDir("/app/bar")
                .endEphemeralContainer()
                .addNewEphemeralContainer()
                .withName("foo")
                .withWorkingDir("/app/ephemeral-foo")
                .endEphemeralContainer()
                .endSpec()
                .build();

        Optional<String> wd = PodContainerSource.lookupContainerWorkingDir(pod, "foo");
        assertTrue(wd.isPresent());
        assertEquals("/app/foo", wd.get());

        // should use TestPodContainerSource to find ephemeral container
        wd = PodContainerSource.lookupContainerWorkingDir(pod, "bar");
        assertTrue(wd.isPresent());
        assertEquals("/app/bar", wd.get());

        // no named container
        wd = PodContainerSource.lookupContainerWorkingDir(pod, "fish");
        assertFalse(wd.isPresent());
    }

    @Test
    void lookupContainerStatus() {
        Pod pod = new PodBuilder()
                .withNewStatus()
                .addNewContainerStatus()
                .withName("foo")
                .withNewState()
                .withNewRunning()
                .endRunning()
                .endState()
                .endContainerStatus()
                .addNewEphemeralContainerStatus()
                .withName("bar")
                .withNewState()
                .withNewTerminated()
                .endTerminated()
                .endState()
                .endEphemeralContainerStatus()
                .endStatus()
                .build();

        Optional<ContainerStatus> status = PodContainerSource.lookupContainerStatus(pod, "foo");
        assertTrue(status.isPresent());
        assertEquals("foo", status.get().getName());

        // should use TestPodContainerSource to find ephemeral container
        status = PodContainerSource.lookupContainerStatus(pod, "bar");
        assertTrue(status.isPresent());
        assertEquals("bar", status.get().getName());

        // no named container
        status = PodContainerSource.lookupContainerStatus(pod, "fish");
        assertFalse(status.isPresent());
    }

    @WithoutJenkins
    @Test
    void defaultPodContainerSourceGetContainerWorkingDir() {
        Pod pod = new PodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName("foo")
                .withWorkingDir("/app/foo")
                .endContainer()
                .addNewEphemeralContainer()
                .withName("bar")
                .withWorkingDir("/app/bar")
                .endEphemeralContainer()
                .endSpec()
                .build();

        PodContainerSource.DefaultPodContainerSource source = new PodContainerSource.DefaultPodContainerSource();
        Optional<String> wd = source.getContainerWorkingDir(pod, "foo");
        assertTrue(wd.isPresent());
        assertEquals("/app/foo", wd.get());

        // should not return ephemeral container
        wd = source.getContainerWorkingDir(pod, "bar");
        assertFalse(wd.isPresent());

        // no named container
        wd = source.getContainerWorkingDir(pod, "fish");
        assertFalse(wd.isPresent());
    }

    @WithoutJenkins
    @Test
    void defaultPodContainerSourceGetContainerStatus() {
        Pod pod = new PodBuilder()
                .withNewStatus()
                .addNewContainerStatus()
                .withName("foo")
                .withNewState()
                .withNewRunning()
                .endRunning()
                .endState()
                .endContainerStatus()
                .addNewEphemeralContainerStatus()
                .withName("bar")
                .withNewState()
                .withNewTerminated()
                .endTerminated()
                .endState()
                .endEphemeralContainerStatus()
                .endStatus()
                .build();

        PodContainerSource.DefaultPodContainerSource source = new PodContainerSource.DefaultPodContainerSource();
        Optional<ContainerStatus> status = source.getContainerStatus(pod, "foo");
        assertTrue(status.isPresent());
        assertEquals("foo", status.get().getName());

        // should not return ephemeral container
        status = source.getContainerStatus(pod, "bar");
        assertFalse(status.isPresent());

        // no named container
        status = source.getContainerStatus(pod, "fish");
        assertFalse(status.isPresent());
    }

    @TestExtension
    public static class TestPodContainerSource extends PodContainerSource {

        @Override
        public Optional<String> getContainerWorkingDir(@NonNull Pod pod, @NonNull String containerName) {
            return pod.getSpec().getEphemeralContainers().stream()
                    .filter(c -> StringUtils.equals(c.getName(), containerName))
                    .findAny()
                    .map(EphemeralContainer::getWorkingDir);
        }

        @Override
        public Optional<ContainerStatus> getContainerStatus(@NonNull Pod pod, @NonNull String containerName) {
            PodStatus podStatus = pod.getStatus();
            if (podStatus == null) {
                return Optional.empty();
            }

            return podStatus.getEphemeralContainerStatuses().stream()
                    .filter(cs -> StringUtils.equals(cs.getName(), containerName))
                    .findFirst();
        }
    }
}
