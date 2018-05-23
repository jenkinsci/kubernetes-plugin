package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.LoggerRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;

public class PodTemplateBuilderTest {

    private static final String AGENT_NAME = "jenkins-agent";
    private static final String AGENT_SECRET = "xxx";
    private static final String JENKINS_URL = "http://jenkins.example.com";

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public LoggerRule logs = new LoggerRule().record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()),
            Level.ALL);

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
        slave = null;
        PodTemplate template = new PodTemplate();
        template.setYaml(new String(IOUtils.toByteArray(getClass().getResourceAsStream("pod-busybox.yaml"))));
        Pod pod = new PodTemplateBuilder(template).build();
        validatePod(pod);
    }

    @Test
    public void testBuildFromYaml() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setYaml(new String(IOUtils.toByteArray(getClass().getResourceAsStream("pod-busybox.yaml"))));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();
        validatePod(pod);
    }

    private void setupStubs() {
        when(cloud.getJenkinsUrlOrDie()).thenReturn(JENKINS_URL);
        when(computer.getName()).thenReturn(AGENT_NAME);
        when(computer.getJnlpMac()).thenReturn(AGENT_SECRET);
        when(slave.getComputer()).thenReturn(computer);
        when(slave.getKubernetesCloud()).thenReturn(cloud);
    }

    private void validatePod(Pod pod) {
        assertThat(pod.getMetadata().getLabels(), hasEntry("some-label", "some-label-value"));
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));


        // check containers
        Map<String, Container> containers = pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
        assertEquals(2, containers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals("jenkins/jnlp-slave:alpine", containers.get("jnlp").getImage());

        // check volumes and volume mounts
        Map<String, Volume> volumes = pod.getSpec().getVolumes().stream()
                .collect(Collectors.toMap(Volume::getName, Function.identity()));
        assertEquals(3, volumes.size());
        assertNotNull(volumes.get("workspace-volume"));
        assertNotNull(volumes.get("empty-volume"));
        assertNotNull(volumes.get("host-volume"));

        List<VolumeMount> mounts = containers.get("busybox").getVolumeMounts();
        assertEquals(2, mounts.size());
        assertEquals(new VolumeMount("/container/data", "host-volume", null, null), mounts.get(0));
        assertEquals(new VolumeMount("/home/jenkins", "workspace-volume", false, null), mounts.get(1));

        validateJnlpContainer(containers.get("jnlp"), slave);
    }

    private void validateJnlpContainer(Container jnlp, KubernetesSlave slave) {
        assertEquals("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), 1, jnlp.getVolumeMounts().size());
        assertThat(jnlp.getCommand(), empty());
        List<EnvVar> envVars = Lists.newArrayList( //
                new EnvVar("HOME", "/home/jenkins", null) //
        );
        if (slave != null) {
            assertThat(jnlp.getArgs(), empty());
            envVars.add(new EnvVar("JENKINS_URL", JENKINS_URL, null));
            envVars.add(new EnvVar("JENKINS_SECRET", AGENT_SECRET, null));
            envVars.add(new EnvVar("JENKINS_NAME", AGENT_NAME, null));
        } else {
            assertThat(jnlp.getArgs(), empty());
        }
        assertThat(jnlp.getEnv(), hasItems(envVars.toArray(new EnvVar[envVars.size()])));
    }

    @Test
    public void testOverridesFromYaml() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setYaml(new String(IOUtils.toByteArray(getClass().getResourceAsStream("pod-overrides.yaml"))));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();

        Map<String, Container> containers = pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
        assertEquals(1, containers.size());
        validateJnlpContainer(containers.get("jnlp"), slave);
    }

    @Test
    public void testOverridesContainerSpec() throws Exception {
        PodTemplate template = new PodTemplate();
        ContainerTemplate cT = new ContainerTemplate("jnlp", "jenkinsci/jnlp-slave:latest");
        template.setContainers(Lists.newArrayList(cT));
        template.setYaml(new String(IOUtils.toByteArray(getClass().getResourceAsStream("pod-overrides.yaml"))));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();

        Map<String, Container> containers = pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
        assertEquals(1, containers.size());
        validateJnlpContainer(containers.get("jnlp"), slave);
    }
}
