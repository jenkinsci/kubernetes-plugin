package org.csanchez.jenkins.plugins.kubernetes.volumes;

import static org.junit.Assert.assertEquals;

import io.fabric8.kubernetes.api.model.Volume;
import org.junit.Test;

public class GenericEphemeralVolumeTest {

    @Test
    public void testCreatesVolumeCorrectly() {

        GenericEphemeralVolume genericEphemeralVolume = new GenericEphemeralVolume();
        genericEphemeralVolume.setAccessModes("ReadWriteOnce");
        genericEphemeralVolume.setRequestsSize("1Gi");
        genericEphemeralVolume.setStorageClassName("standard");
        genericEphemeralVolume.setMountPath("/tmp");

        Volume volume = genericEphemeralVolume.buildVolume("testvolume", "testpod");

        assertEquals("testvolume", volume.getName());
        assertEquals(
                "ReadWriteOnce",
                volume.getEphemeral()
                        .getVolumeClaimTemplate()
                        .getSpec()
                        .getAccessModes()
                        .get(0));
        assertEquals(
                "standard",
                volume.getEphemeral().getVolumeClaimTemplate().getSpec().getStorageClassName());
        assertEquals(
                "1Gi",
                volume.getEphemeral()
                        .getVolumeClaimTemplate()
                        .getSpec()
                        .getResources()
                        .getRequests()
                        .get("storage")
                        .toString());
    }
}
