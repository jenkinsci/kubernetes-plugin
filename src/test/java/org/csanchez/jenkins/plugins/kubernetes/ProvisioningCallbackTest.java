package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import hudson.model.labels.LabelAtom;

public class ProvisioningCallbackTest {

    private KubernetesCloud cloud = new KubernetesCloud("test");
    private ProvisioningCallback callback = new ProvisioningCallback(cloud, new PodTemplate(), new LabelAtom("test"));

    @Test
    public void testParseDockerCommand() {
        assertNull(callback.parseDockerCommand(""));
        assertNull(callback.parseDockerCommand(null));
        assertEquals(ImmutableList.of("bash"), callback.parseDockerCommand("bash"));
        assertEquals(ImmutableList.of("bash", "-c", "x y"), callback.parseDockerCommand("bash -c \"x y\""));
        assertEquals(ImmutableList.of("a", "b", "c", "d"), callback.parseDockerCommand("a b c d"));
    }

    @Test
    public void testParseLivenessProbe() {
        assertNull(callback.parseLivenessProbe(""));
        assertNull(callback.parseLivenessProbe(null));
        assertEquals(ImmutableList.of("docker", "info"), callback.parseLivenessProbe("docker info"));
        assertEquals(ImmutableList.of("echo", "I said: 'I am alive'"),
                callback.parseLivenessProbe("echo \"I said: 'I am alive'\""));
        assertEquals(ImmutableList.of("docker", "--version"), callback.parseLivenessProbe("docker --version"));
        assertEquals(ImmutableList.of("curl", "-k", "--silent", "--output=/dev/null", "https://localhost:8080"),
                callback.parseLivenessProbe("curl -k --silent --output=/dev/null \"https://localhost:8080\""));
    }

}
