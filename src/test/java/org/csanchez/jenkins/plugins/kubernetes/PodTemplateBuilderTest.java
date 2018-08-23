package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.EmptyDirWorkspaceVolume;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;

public class PodTemplateBuilderTest {

    private static final String AGENT_NAME = "jenkins-agent";
    private static final String AGENT_SECRET = "xxx";
    private static final String JENKINS_URL = "http://jenkins.example.com";

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public LoggerRule logs = new LoggerRule().record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()),
            Level.ALL);

    @Spy
    private KubernetesCloud cloud = new KubernetesCloud("test");

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
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));

        Map<String, Container> containers = pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
        assertEquals(2, containers.size());

        Container container0 = containers.get("busybox");
        assertNotNull(container0.getResources());
        assertNotNull(container0.getResources().getRequests());
        assertNotNull(container0.getResources().getLimits());
        assertThat(container0.getResources().getRequests(), hasEntry("example.com/dongle", new Quantity("42")));
        assertThat(container0.getResources().getLimits(), hasEntry("example.com/dongle", new Quantity("42")));
    }

    @Test
    @Issue("JENKINS-50525")
    public void testBuildWithCustomWorkspaceVolume() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setCustomWorkspaceVolumeEnabled(true);
        template.setWorkspaceVolume(new EmptyDirWorkspaceVolume(false));
        ContainerTemplate containerTemplate = new ContainerTemplate("name", "image");
        containerTemplate.setWorkingDir("");
        template.getContainers().add(containerTemplate);
        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();
        List<Container> containers = pod.getSpec().getContainers();
        assertEquals(2, containers.size());
        Container container0 = containers.get(0);
        Container container1 = containers.get(1);

        ImmutableList<VolumeMount> volumeMounts = ImmutableList.of(new VolumeMountBuilder()
                .withMountPath("/home/jenkins").withName("workspace-volume").withReadOnly(false).build());

        assertEquals(volumeMounts, container0.getVolumeMounts());
        assertEquals(volumeMounts, container1.getVolumeMounts());
    }

    @Test
    public void testBuildFromTemplate() throws Exception {
        PodTemplate template = new PodTemplate();

        List<PodVolume> volumes = new ArrayList<PodVolume>();
        volumes.add(new HostPathVolume("/host/data", "/container/data"));
        volumes.add(new EmptyDirVolume("/empty/dir", false));
        template.setVolumes(volumes);

        List<ContainerTemplate> containers = new ArrayList<ContainerTemplate>();
        ContainerTemplate busyboxContainer = new ContainerTemplate("busybox", "busybox");
        busyboxContainer.setCommand("cat");
        busyboxContainer.setTtyEnabled(true);
        List<TemplateEnvVar> envVars = new ArrayList<TemplateEnvVar>();
        envVars.add(new KeyValueEnvVar("CONTAINER_ENV_VAR", "container-env-var-value"));
        busyboxContainer.setEnvVars(envVars);
        containers.add(busyboxContainer);
        template.setContainers(containers);

        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();
        pod.getMetadata().setLabels(ImmutableMap.of("some-label","some-label-value"));
        validatePod(pod, false);
    }

    private void setupStubs() {
        doReturn(JENKINS_URL).when(cloud).getJenkinsUrlOrDie();
        when(computer.getName()).thenReturn(AGENT_NAME);
        when(computer.getJnlpMac()).thenReturn(AGENT_SECRET);
        when(slave.getComputer()).thenReturn(computer);
        when(slave.getKubernetesCloud()).thenReturn(cloud);
    }

    private void validatePod(Pod pod) {
        validatePod(pod, true);
    }

    private void validatePod(Pod pod, boolean fromYaml) {
        assertThat(pod.getMetadata().getLabels(), hasEntry("some-label", "some-label-value"));

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
        if (fromYaml) {
            assertNotNull(volumes.get("empty-volume"));
            assertNotNull(volumes.get("host-volume"));
        } else {
            assertNotNull(volumes.get("volume-0"));
            assertNotNull(volumes.get("volume-1"));
        }

        List<VolumeMount> mounts = containers.get("busybox").getVolumeMounts();
        if (fromYaml) {
            assertEquals(2, mounts.size());
            assertEquals(new VolumeMountBuilder().withMountPath("/container/data").withName("host-volume").build(),
                    mounts.get(0));
            assertEquals(new VolumeMountBuilder().withMountPath("/home/jenkins").withName("workspace-volume")
                    .withReadOnly(false).build(), mounts.get(1));
        } else {
            assertEquals(3, mounts.size());
            assertEquals(new VolumeMountBuilder().withMountPath("/container/data").withName("volume-0").withReadOnly(false).build(),
                    mounts.get(0));
            assertEquals(new VolumeMountBuilder().withMountPath("/empty/dir").withName("volume-1").withReadOnly(false).build(),
                    mounts.get(1));
            assertEquals(new VolumeMountBuilder().withMountPath("/home/jenkins").withName("workspace-volume")
                    .withReadOnly(false).build(), mounts.get(2));
        }

        List<VolumeMount> jnlpMounts = containers.get("jnlp").getVolumeMounts();
        if (fromYaml) {
            assertEquals(1, jnlpMounts.size());
            assertEquals(new VolumeMountBuilder().withMountPath("/home/jenkins").withName("workspace-volume")
                    .withReadOnly(false).build(), jnlpMounts.get(0));
        } else {
            assertEquals(3, jnlpMounts.size());
            assertEquals(new VolumeMountBuilder().withMountPath("/container/data").withName("volume-0").withReadOnly(false).build(),
                    jnlpMounts.get(0));
            assertEquals(new VolumeMountBuilder().withMountPath("/empty/dir").withName("volume-1").withReadOnly(false).build(),
                    jnlpMounts.get(1));
            assertEquals(new VolumeMountBuilder().withMountPath("/home/jenkins").withName("workspace-volume")
                    .withReadOnly(false).build(), jnlpMounts.get(2));
        }

        validateJnlpContainer(containers.get("jnlp"), slave);
    }

    private void validateJnlpContainer(Container jnlp, KubernetesSlave slave) {
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
        assertThat(jnlp.getEnv(), containsInAnyOrder(envVars.toArray(new EnvVar[envVars.size()])));
        if (jnlp.getResources() != null) {
            if (jnlp.getResources().getRequests() != null) {
                assertFalse(jnlp.getResources().getRequests().containsValue(new Quantity("")));
            }
            if (jnlp.getResources().getLimits() != null) {
                assertFalse(jnlp.getResources().getLimits().containsValue(new Quantity("")));
            }
        }
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
        Container jnlp = containers.get("jnlp");
		assertEquals("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), 1, jnlp.getVolumeMounts().size());
        validateJnlpContainer(jnlp, slave);
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
        Container jnlp = containers.get("jnlp");
		assertEquals("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), 1, jnlp.getVolumeMounts().size());
        validateJnlpContainer(jnlp, slave);
    }
}
