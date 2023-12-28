package org.csanchez.jenkins.plugins.kubernetes.casc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import java.util.List;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.volumes.GenericEphemeralVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.RestartableJenkinsRule;

@RunWith(Parameterized.class)
public class WorkspaceVolumeCasCTest extends RoundTripAbstractTest {
    final WorkspaceVolumeStrategy strategy;
    final String resource;

    public WorkspaceVolumeCasCTest(WorkspaceVolumeStrategy strategy, String resource) {
        this.strategy = strategy;
        this.resource = resource;
    }

    @Parameterized.Parameters(name = "{index}: {1}")
    public static Object[] permutations() {
        return new Object[][] {
            {new DynamicPVCWorkspaceVolumeStrategy(), "dynamicPVC"},
            {new EmptyDirWorkspaceVolumeStrategy(), "emptyDir"},
            {new EmptyDirWorkspaceVolumeStrategy(Boolean.TRUE), "emptyDir_memory"},
            {new HostPathWorkspaceVolumeStrategy(), "hostPath"},
            {new NfsWorkspaceVolumeStrategy(), "nfs"},
            {new PVCWorkspaceVolumeStrategy(), "pvc"},
            {new GenericEphemeralWorkspaceVolumeStrategy(), "genericEphemeral"},
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
