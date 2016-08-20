package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import jenkins.model.Jenkins;

public class KubernetesCloudTest {

    private KubernetesCloud cloud = new KubernetesCloud("test", null, "http://localhost:8080", "default", null, "", 0,
            0, /* retentionTimeoutMinutes= */ 5);

    @Test
    public void testParseDockerCommand() {
        assertNull(cloud.parseDockerCommand(""));
        assertNull(cloud.parseDockerCommand(null));
        assertEquals(ImmutableList.of("bash"), cloud.parseDockerCommand("bash"));
        assertEquals(ImmutableList.of("bash", "-c", "x y"), cloud.parseDockerCommand("bash -c \"x y\""));
        assertEquals(ImmutableList.of("a", "b", "c", "d"), cloud.parseDockerCommand("a b c d"));
    }

    @Test
    public void testUpgradeFrom_0_8() {
        KubernetesCloud fromXML = (KubernetesCloud) Jenkins.XSTREAM2
                .fromXML(this.getClass().getClassLoader().getResourceAsStream("config-0.8.xml"));
        List<PodTemplate> templates = fromXML.getTemplates();
        assertEquals(1, templates.size());
        PodTemplate podTemplate = templates.get(0);
        assertEquals(1, podTemplate.getContainers().size());
        ContainerTemplate containerTemplate = podTemplate.getContainers().get(0);
        assertEquals("jenkinsci/jnlp-slave", containerTemplate.getImage());
        assertEquals("java", containerTemplate.getName());
        assertEquals(Arrays.asList(new ContainerEnvVar("a", "b"), new ContainerEnvVar("c", "d")),
                containerTemplate.getEnvVars());
    }
}
