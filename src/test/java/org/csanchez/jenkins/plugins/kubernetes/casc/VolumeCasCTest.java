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
import org.csanchez.jenkins.plugins.kubernetes.volumes.*;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@ParameterizedClass(name = "{index}: {1}")
@MethodSource("permutations")
class VolumeCasCTest extends AbstractRoundTripTest {

    @Parameter(0)
    private VolumeStrategy strategy;

    @Parameter(1)
    private String resource;

    static Stream<Arguments> permutations() {
        return Stream.of(
                Arguments.of(new EmptyDirVolumeStrategy(), "emptyDir"),
                Arguments.of(new EmptyDirVolumeStrategy(Boolean.TRUE), "emptyDir_memory"),
                Arguments.of(new ConfigMapVolumeStrategy(), "configMap"),
                Arguments.of(new HostPathVolumeStrategy(), "hostPath"),
                Arguments.of(new NfsVolumeStrategy(), "nfs"),
                Arguments.of(new PVCVolumeStrategy(), "pvc"),
                Arguments.of(new GenericEphemeralVolumeStrategy(), "genericEphemeral"));
    }

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule r, String configContent) {
        KubernetesCloud cloud = r.jenkins.clouds.get(KubernetesCloud.class);
        assertNotNull(cloud);
        List<PodTemplate> templates = cloud.getTemplates();
        assertNotNull(templates);
        assertEquals(1, templates.size());
        PodTemplate podTemplate = templates.get(0);
        List<PodVolume> volumes = podTemplate.getVolumes();
        assertEquals(1, volumes.size());
        strategy.verify(volumes.get(0));
    }

    @Override
    protected String configResource() {
        return "casc_volume_" + resource + ".yaml";
    }

    @Override
    protected String stringInLogExpected() {
        return "KubernetesCloud";
    }

    abstract static class VolumeStrategy<T> {
        void verify(PodVolume volume) {
            _verify(volume);
            assertEquals("/mnt/path", volume.getMountPath());
        }

        abstract void _verify(PodVolume volume);
    }

    static class EmptyDirVolumeStrategy extends VolumeStrategy {
        private Boolean memory;

        public EmptyDirVolumeStrategy() {
            this(Boolean.FALSE);
        }

        public EmptyDirVolumeStrategy(Boolean memory) {
            this.memory = memory;
        }

        @Override
        void _verify(PodVolume volume) {
            assertThat(volume, instanceOf(EmptyDirVolume.class));
            EmptyDirVolume d = (EmptyDirVolume) volume;
            assertEquals(memory, d.getMemory());
        }
    }

    static class ConfigMapVolumeStrategy extends VolumeStrategy {

        @Override
        void _verify(PodVolume volume) {
            assertThat(volume, instanceOf(ConfigMapVolume.class));
            ConfigMapVolume d = (ConfigMapVolume) volume;
            assertEquals("my-configmap", d.getConfigMapName());
        }
    }

    static class HostPathVolumeStrategy extends VolumeStrategy {

        @Override
        void _verify(PodVolume volume) {
            assertThat(volume, instanceOf(HostPathVolume.class));
            HostPathVolume d = (HostPathVolume) volume;
            assertEquals("/host/path", d.getHostPath());
        }
    }

    static class NfsVolumeStrategy extends VolumeStrategy {

        @Override
        void _verify(PodVolume volume) {
            assertThat(volume, instanceOf(NfsVolume.class));
            NfsVolume d = (NfsVolume) volume;
            assertEquals("serverAddress", d.getServerAddress());
            assertEquals("/path", d.getServerPath());
        }
    }

    static class PVCVolumeStrategy extends VolumeStrategy {

        @Override
        void _verify(PodVolume volume) {
            assertThat(volume, instanceOf(PersistentVolumeClaim.class));
            PersistentVolumeClaim d = (PersistentVolumeClaim) volume;
            assertEquals("my-claim", d.getClaimName());
        }
    }

    static class GenericEphemeralVolumeStrategy extends VolumeStrategy {

        @Override
        void _verify(PodVolume volume) {
            assertThat(volume, instanceOf(GenericEphemeralVolume.class));
            GenericEphemeralVolume d = (GenericEphemeralVolume) volume;
            assertEquals("ReadWriteMany", d.getAccessModes());
            assertEquals("10Gi", d.getRequestsSize());
            assertEquals("/mnt/path", d.getMountPath());
            assertEquals("test-storageclass", d.getStorageClassName());
        }
    }
}
