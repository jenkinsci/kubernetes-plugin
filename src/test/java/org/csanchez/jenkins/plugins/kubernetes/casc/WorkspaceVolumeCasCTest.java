package org.csanchez.jenkins.plugins.kubernetes.casc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import java.util.List;
import java.util.stream.Stream;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.*;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@ParameterizedClass(name = "{index}: {1}")
@MethodSource("permutations")
public class WorkspaceVolumeCasCTest extends AbstractRoundTripTest {

    @Parameter(0)
    private WorkspaceVolumeStrategy strategy;

    @Parameter(1)
    private String resource;

    static Stream<Arguments> permutations() {
        return Stream.of(
                Arguments.of(new DynamicPVCWorkspaceVolumeStrategy(), "dynamicPVC"),
                Arguments.of(new EmptyDirWorkspaceVolumeStrategy(), "emptyDir"),
                Arguments.of(new EmptyDirWorkspaceVolumeStrategy(Boolean.TRUE), "emptyDir_memory"),
                Arguments.of(new HostPathWorkspaceVolumeStrategy(), "hostPath"),
                Arguments.of(new NfsWorkspaceVolumeStrategy(), "nfs"),
                Arguments.of(new PVCWorkspaceVolumeStrategy(), "pvc"),
                Arguments.of(new GenericEphemeralWorkspaceVolumeStrategy(), "genericEphemeral"));
    }

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule r, String configContent) {
        KubernetesCloud cloud = r.jenkins.clouds.get(KubernetesCloud.class);
        assertNotNull(cloud);
        List<PodTemplate> templates = cloud.getTemplates();
        assertNotNull(templates);
        assertEquals(1, templates.size());
        PodTemplate podTemplate = templates.get(0);
        strategy.verify(podTemplate.getWorkspaceVolume());
    }

    @Override
    protected String configResource() {
        return "casc_workspaceVolume_" + resource + ".yaml";
    }

    @Override
    protected String stringInLogExpected() {
        return "KubernetesCloud";
    }

    abstract static class WorkspaceVolumeStrategy<T> {
        abstract void verify(WorkspaceVolume workspaceVolume);
    }

    static class DynamicPVCWorkspaceVolumeStrategy extends WorkspaceVolumeStrategy {
        @Override
        void verify(WorkspaceVolume workspaceVolume) {
            assertThat(workspaceVolume, instanceOf(DynamicPVCWorkspaceVolume.class));
            DynamicPVCWorkspaceVolume d = (DynamicPVCWorkspaceVolume) workspaceVolume;
            assertEquals("ReadWriteOnce", d.getAccessModes());
            assertEquals("1", d.getRequestsSize());
            assertEquals("hostpath", d.getStorageClassName());
        }
    }

    static class EmptyDirWorkspaceVolumeStrategy extends WorkspaceVolumeStrategy {
        private Boolean memory;

        public EmptyDirWorkspaceVolumeStrategy() {
            this(Boolean.FALSE);
        }

        public EmptyDirWorkspaceVolumeStrategy(Boolean memory) {
            this.memory = memory;
        }

        @Override
        void verify(WorkspaceVolume workspaceVolume) {
            assertThat(workspaceVolume, instanceOf(EmptyDirWorkspaceVolume.class));
            EmptyDirWorkspaceVolume d = (EmptyDirWorkspaceVolume) workspaceVolume;
            assertEquals(memory, d.getMemory());
        }
    }

    static class HostPathWorkspaceVolumeStrategy extends WorkspaceVolumeStrategy {

        @Override
        void verify(WorkspaceVolume workspaceVolume) {
            assertThat(workspaceVolume, instanceOf(HostPathWorkspaceVolume.class));
            HostPathWorkspaceVolume d = (HostPathWorkspaceVolume) workspaceVolume;
            assertEquals("/host/path", d.getHostPath());
        }
    }

    static class NfsWorkspaceVolumeStrategy extends WorkspaceVolumeStrategy {

        @Override
        void verify(WorkspaceVolume workspaceVolume) {
            assertThat(workspaceVolume, instanceOf(NfsWorkspaceVolume.class));
            NfsWorkspaceVolume d = (NfsWorkspaceVolume) workspaceVolume;
            assertEquals("serverAddress", d.getServerAddress());
            assertEquals("/path", d.getServerPath());
        }
    }

    static class PVCWorkspaceVolumeStrategy extends WorkspaceVolumeStrategy {

        @Override
        void verify(WorkspaceVolume workspaceVolume) {
            assertThat(workspaceVolume, instanceOf(PersistentVolumeClaimWorkspaceVolume.class));
            PersistentVolumeClaimWorkspaceVolume d = (PersistentVolumeClaimWorkspaceVolume) workspaceVolume;
            assertEquals("my-claim", d.getClaimName());
        }
    }

    static class GenericEphemeralWorkspaceVolumeStrategy extends WorkspaceVolumeStrategy {

        @Override
        void verify(WorkspaceVolume workspaceVolume) {
            assertThat(workspaceVolume, instanceOf(GenericEphemeralWorkspaceVolume.class));
            GenericEphemeralWorkspaceVolume d = (GenericEphemeralWorkspaceVolume) workspaceVolume;
            assertEquals("test-storageclass", d.getStorageClassName());
            assertEquals("ReadWriteMany", d.getAccessModes());
            assertEquals("10Gi", d.getRequestsSize());
        }
    }
}
