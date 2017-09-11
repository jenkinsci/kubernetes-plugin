package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KubernetesLauncherTest {
    @Test
    public void testParseDockerCommand() {
        assertNull(KubernetesLauncher.parseDockerCommand(""));
        assertNull(KubernetesLauncher.parseDockerCommand(null));
        assertEquals(ImmutableList.of("bash"), KubernetesLauncher.parseDockerCommand("bash"));
        assertEquals(ImmutableList.of("bash", "-c", "x y"), KubernetesLauncher.parseDockerCommand("bash -c \"x y\""));
        assertEquals(ImmutableList.of("a", "b", "c", "d"), KubernetesLauncher.parseDockerCommand("a b c d"));
    }

    @Test
    public void testParseLivenessProbe() {
        assertNull(KubernetesLauncher.parseLivenessProbe(""));
        assertNull(KubernetesLauncher.parseLivenessProbe(null));
        assertEquals(ImmutableList.of("docker", "info"), KubernetesLauncher.parseLivenessProbe("docker info"));
        assertEquals(ImmutableList.of("echo", "I said: 'I am alive'"),
                KubernetesLauncher.parseLivenessProbe("echo \"I said: 'I am alive'\""));
        assertEquals(ImmutableList.of("docker", "--version"), KubernetesLauncher.parseLivenessProbe("docker --version"));
        assertEquals(ImmutableList.of("curl", "-k", "--silent", "--output=/dev/null", "https://localhost:8080"),
                KubernetesLauncher.parseLivenessProbe("curl -k --silent --output=/dev/null \"https://localhost:8080\""));
    }

}
