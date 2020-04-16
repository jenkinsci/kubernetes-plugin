package org.csanchez.jenkins.plugins.kubernetes.casc;

import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.volumes.ConfigMapVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.NfsVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PersistentVolumeClaim;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class VolumeCasCTest extends RoundTripAbstractTest {
    final VolumeStrategy strategy;
    final String resource;

    public VolumeCasCTest(VolumeStrategy strategy, String resource) {
        this.strategy = strategy;
        this.resource = resource;
    }

    @Parameterized.Parameters(name = "{index}: {1}")
    public static Object[] permutations() {
        return new Object[][] {
                {new EmptyDirVolumeStrategy(), "emptyDir"},
                {new EmptyDirVolumeStrategy(Boolean.TRUE), "emptyDir_memory"},
                {new ConfigMapVolumeStrategy(), "configMap"},
                {new HostPathVolumeStrategy(), "hostPath"},
                {new NfsVolumeStrategy(), "nfs"},
                {new PVCVolumeStrategy(), "pvc"},
        };
    }

    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule r, String configContent) {
        KubernetesCloud cloud = r.j.jenkins.clouds.get(KubernetesCloud.class);
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
            assertEquals("/path",d.getServerPath());
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

}
