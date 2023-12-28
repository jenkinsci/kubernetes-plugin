package org.csanchez.jenkins.plugins.kubernetes.volumes.workspace;

import io.fabric8.kubernetes.api.model.Volume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GenericEphemeralWorkspaceVolumeTest {

    @Test
    public void testCreatesVolumeCorrectly() {

        GenericEphemeralWorkspaceVolume genericEphemeralWorkspaceVolume = new GenericEphemeralWorkspaceVolume(
                "test-storageclass",
                "1Gi",
                "ReadWriteOnce"
        );

        Volume volume = genericEphemeralWorkspaceVolume.buildVolume("testvolume", "testpod");

        assertEquals("testvolume", volume.getName());
        assertEquals("ReadWriteOnce", volume.getEphemeral().getVolumeClaimTemplate().getSpec().getAccessModes().get(0));
        assertEquals("test-storageclass", volume.getEphemeral().getVolumeClaimTemplate().getSpec().getStorageClassName());
        assertEquals("1Gi", volume.getEphemeral().getVolumeClaimTemplate().getSpec().getResources().getRequests().get("storage").toString());
    }
}
