package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.fabric8.kubernetes.api.model.EphemeralContainer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class PodContainerSourceTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void lookupContainerWorkingDir() {
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

    @WithoutJenkins
    @Test
    public void defaultPodContainerSourceGetContainerWorkingDir() {
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

    @Extension
    public static class TestPodContainerSource extends PodContainerSource {

        @Override
        public Optional<String> getContainerWorkingDir(@NonNull Pod pod, @NonNull String containerName) {
            return pod.getSpec().getEphemeralContainers().stream()
                    .filter(c -> StringUtils.equals(c.getName(), containerName))
                    .findAny()
                    .map(EphemeralContainer::getWorkingDir);
        }
    }
}
