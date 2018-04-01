package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;

public class PodTemplateBuilderTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private KubernetesCloud cloud;

    @Mock
    private KubernetesSlave slave;

    @Mock
    private KubernetesComputer computer;

    @Test
    public void testParseDockerCommand() {
        assertNull(parseDockerCommand(""));
        assertNull(parseDockerCommand(null));
        assertEquals(ImmutableList.of("bash"), parseDockerCommand("bash"));
        assertEquals(ImmutableList.of("bash", "-c", "x y"), parseDockerCommand("bash -c \"x y\""));
        assertEquals(ImmutableList.of("a", "b", "c", "d"), parseDockerCommand("a b c d"));
    }

    @Test
    public void testParseLivenessProbe() {
        assertNull(parseLivenessProbe(""));
        assertNull(parseLivenessProbe(null));
        assertEquals(ImmutableList.of("docker", "info"), parseLivenessProbe("docker info"));
        assertEquals(ImmutableList.of("echo", "I said: 'I am alive'"),
                parseLivenessProbe("echo \"I said: 'I am alive'\""));
        assertEquals(ImmutableList.of("docker", "--version"), parseLivenessProbe("docker --version"));
        assertEquals(ImmutableList.of("curl", "-k", "--silent", "--output=/dev/null", "https://localhost:8080"),
                parseLivenessProbe("curl -k --silent --output=/dev/null \"https://localhost:8080\""));
    }

    @Test
    public void testBuildWithoutSlave() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setYaml(new String(IOUtils.toByteArray(getClass().getResourceAsStream("pod-busybox.yaml"))));
        Pod pod = new PodTemplateBuilder(template).build();
        validatePod(pod);
    }

    @Test
    public void testBuildFromYaml() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setYaml(new String(IOUtils.toByteArray(getClass().getResourceAsStream("pod-busybox.yaml"))));

        when(cloud.getJenkinsUrlOrDie()).thenReturn("http://jenkins.example.com");
        when(computer.getName()).thenReturn("jenkins-agent");
        when(computer.getJnlpMac()).thenReturn("xxx");
        when(slave.getComputer()).thenReturn(computer);
        when(slave.getKubernetesCloud()).thenReturn(cloud);

        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();
        validatePod(pod);
    }

    private void validatePod(Pod pod) {
        assertEquals(ImmutableMap.of("some-label", "some-label-value"), pod.getMetadata().getLabels());

        // check containers
        Map<String, Container> containers = new HashMap<>();
        for (Container c : pod.getSpec().getContainers()) {
            containers.put(c.getName(), c);
        }
        assertEquals(2, containers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals("jenkins/jnlp-slave:alpine", containers.get("jnlp").getImage());

        // check volumes and volume mounts
        Map<String, Volume> volumes = new HashMap<>();
        for (Volume v : pod.getSpec().getVolumes()) {
            volumes.put(v.getName(), v);
        }
        assertEquals(2, volumes.size());
        assertNotNull(volumes.get("workspace-volume"));
        assertNotNull(volumes.get("empty-volume"));

        List<VolumeMount> mounts = containers.get("busybox").getVolumeMounts();
        assertEquals(1, mounts.size());

        mounts = containers.get("jnlp").getVolumeMounts();
        assertEquals(1, mounts.size());
    }
}
