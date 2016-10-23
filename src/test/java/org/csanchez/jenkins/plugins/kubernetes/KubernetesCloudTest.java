package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

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

}
