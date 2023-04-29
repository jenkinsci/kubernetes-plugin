package org.csanchez.jenkins.plugins.kubernetes;

import org.csanchez.jenkins.plugins.kubernetes.volumes.PersistentVolumeClaim;

import static org.junit.Assert.*;
import org.junit.Test;

public class PersistentVolumeClaimTest {

    @Test
    public void testNullSubPathValue() {
	PersistentVolumeClaim persistentVolumeClaim= new PersistentVolumeClaim("oneMountPath", "Myvolume",false);
        assertNull(persistentVolumeClaim.getSubPath());
    }

    @Test
    public void testValidSubPathValue() {
	PersistentVolumeClaim persistentVolumeClaim= new PersistentVolumeClaim("oneMountPath", "Myvolume",false);
	persistentVolumeClaim.setSubPath("miSubpath");
        assertEquals(persistentVolumeClaim.getSubPath(),"miSubpath");
    }

}
