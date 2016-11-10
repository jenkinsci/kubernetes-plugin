package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;

public class KubernetesCloudTest {

    private KubernetesCloud cloud = new KubernetesCloud("test", null, "http://localhost:8080", "default", null, "", 0,
            0, /* retentionTimeoutMinutes= */ 5);


    @Test
    public void testInheritance() {

        ContainerTemplate jnlp = new ContainerTemplate("jnlp", "jnlp:1");
        ContainerTemplate maven = new ContainerTemplate("maven", "maven:1");
        maven.setTtyEnabled(true);
        maven.setCommand("cat");

        PodVolume podVolume = new EmptyDirVolume("/some/path", true);
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setLabel("parent");
        parent.setContainers(Arrays.asList(jnlp));
        parent.setVolumes(Arrays.asList(podVolume));


        ContainerTemplate maven2 = new ContainerTemplate("maven", "maven:2");
        PodTemplate withNewMavenVersion = new PodTemplate();
        withNewMavenVersion.setContainers(Arrays.asList(maven2));

        PodTemplate result = PodTemplateUtils.combine(parent, withNewMavenVersion);


    }

    @Test
    public void testParseDockerCommand() {
        assertNull(cloud.parseDockerCommand(""));
        assertNull(cloud.parseDockerCommand(null));
        assertEquals(ImmutableList.of("bash"), cloud.parseDockerCommand("bash"));
        assertEquals(ImmutableList.of("bash", "-c", "x y"), cloud.parseDockerCommand("bash -c \"x y\""));
        assertEquals(ImmutableList.of("a", "b", "c", "d"), cloud.parseDockerCommand("a b c d"));
    }

}
