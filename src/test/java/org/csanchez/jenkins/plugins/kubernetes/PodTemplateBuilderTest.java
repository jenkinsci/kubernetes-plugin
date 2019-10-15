package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.stream.Collectors.toList;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder.*;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.YamlMergeStrategy;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.EmptyDirWorkspaceVolume;
import org.hamcrest.Matcher;
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
        String yaml = loadYamlFile("pod-busybox.yaml");
        template.setYaml(yaml);
        assertEquals(yaml,template.getYaml());
        Pod pod = new PodTemplateBuilder(template).build();
        validatePod(pod);
    }

    @Test
    public void testBuildFromYaml() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();
        validatePod(pod);
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));

        Map<String, Container> containers = toContainerMap(pod);
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
                .withMountPath("/home/jenkins/agent").withName("workspace-volume").withReadOnly(false).build());

        assertEquals(volumeMounts, container0.getVolumeMounts());
        assertEquals(volumeMounts, container1.getVolumeMounts());
    }

    @Test
    public void testBuildWithDynamicPVCWorkspaceVolume(){
        PodTemplate template = new PodTemplate();
        template.setCustomWorkspaceVolumeEnabled(true);
        template.setWorkspaceVolume(new DynamicPVCWorkspaceVolume(
                null, null,null));
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
                .withMountPath("/home/jenkins/agent").withName("workspace-volume").withReadOnly(false).build());

        assertEquals(volumeMounts, container0.getVolumeMounts());
        assertEquals(volumeMounts, container1.getVolumeMounts());
        assertNotNull(pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim());
    }

    @Test
    public void testBuildFromTemplate() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setRunAsUser(1000L);
        template.setRunAsGroup(1000L);

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
        busyboxContainer.setRunAsUser(2000L);
        busyboxContainer.setRunAsGroup(2000L);
        containers.add(busyboxContainer);
        template.setContainers(containers);

        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();
        pod.getMetadata().setLabels(ImmutableMap.of("some-label","some-label-value"));
        validatePod(pod, false);
    }

    @Test
    public void homeIsSetOnOpenShift() {
        when(slave.getKubernetesCloud()).thenReturn(cloud);
        doReturn(JENKINS_URL).when(cloud).getJenkinsUrlOrDie();
        doReturn(true).when(cloud).isOpenShift();

        PodTemplate template = new PodTemplate();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();

        Map<String, Container> containers = pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
        Container jnlp = containers.get("jnlp");
        assertThat(jnlp.getEnv(), hasItems(new EnvVar("HOME", "/home/jenkins", null)));
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
        Map<String, Container> containers = toContainerMap(pod);
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
        List<VolumeMount> jnlpMounts = containers.get("jnlp").getVolumeMounts();
        VolumeMount workspaceVolume = new VolumeMountBuilder() //
                .withMountPath("/home/jenkins/agent").withName("workspace-volume").withReadOnly(false).build();

        // when using yaml we don't mount all volumes, just the ones explicitly listed
        if (fromYaml) {
            assertThat(mounts, containsInAnyOrder(workspaceVolume, //
                    new VolumeMountBuilder().withMountPath("/container/data").withName("host-volume").build()));
            assertThat(jnlpMounts, containsInAnyOrder(workspaceVolume));
        } else {
            List<Matcher<? super VolumeMount>> volumeMounts = Arrays.asList( //
                    equalTo(workspaceVolume), //
                    equalTo(new VolumeMountBuilder() //
                            .withMountPath("/container/data").withName("volume-0").withReadOnly(false).build()),
                    equalTo(new VolumeMountBuilder() //
                            .withMountPath("/empty/dir").withName("volume-1").withReadOnly(false).build()));
            assertThat(mounts, containsInAnyOrder(volumeMounts));
            assertThat(jnlpMounts, containsInAnyOrder(volumeMounts));
        }

        assertEquals(1000L, (Object) pod.getSpec().getSecurityContext().getRunAsUser());
        assertEquals(1000L, (Object) pod.getSpec().getSecurityContext().getRunAsGroup());
        assertEquals(2000L, (Object) containers.get("busybox").getSecurityContext().getRunAsUser());
        assertEquals(2000L, (Object) containers.get("busybox").getSecurityContext().getRunAsGroup());

        validateContainers(pod, slave);
    }

    private void validateContainers(Pod pod, KubernetesSlave slave) {
        String[] exclusions = new String[] {"JENKINS_URL", "JENKINS_SECRET", "JENKINS_NAME", "JENKINS_AGENT_NAME", "JENKINS_AGENT_WORKDIR"};
        for (Container c : pod.getSpec().getContainers()) {
            if ("jnlp".equals(c.getName())) {
                validateJnlpContainer(c, slave);
            } else {
                List<EnvVar> env = c.getEnv();
                assertThat(env.stream().map(EnvVar::getName).collect(toList()), everyItem(not(isIn(exclusions))));
            }
        }
    }

    private void validateJnlpContainer(Container jnlp, KubernetesSlave slave) {
        assertThat(jnlp.getCommand(), empty());
        List<EnvVar> envVars = Lists.newArrayList();
        if (slave != null) {
            assertThat(jnlp.getArgs(), empty());
            envVars.add(new EnvVar("JENKINS_URL", JENKINS_URL, null));
            envVars.add(new EnvVar("JENKINS_SECRET", AGENT_SECRET, null));
            envVars.add(new EnvVar("JENKINS_NAME", AGENT_NAME, null));
            envVars.add(new EnvVar("JENKINS_AGENT_NAME", AGENT_NAME, null));
            envVars.add(new EnvVar("JENKINS_AGENT_WORKDIR", ContainerTemplate.DEFAULT_WORKING_DIR, null));
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
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();

        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
        assertThat("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), jnlp.getVolumeMounts(), hasSize(1));
        assertEquals(new Quantity("2"), jnlp.getResources().getLimits().get("cpu"));
        assertEquals(new Quantity("2Gi"), jnlp.getResources().getLimits().get("memory"));
        assertEquals(new Quantity("200m"), jnlp.getResources().getRequests().get("cpu"));
        assertEquals(new Quantity("256Mi"), jnlp.getResources().getRequests().get("memory"));
        validateContainers(pod, slave);
    }

    /**
     * This is counter intuitive, the yaml contents are ignored because the parent fields are merged first with the
     * child ones. Then the fields override what is defined in the yaml, so in effect the parent resource limits and
     * requests are used.
     */
    @Test
    public void testInheritsFromWithYaml() throws Exception {
        PodTemplate parent = new PodTemplate();
        ContainerTemplate container1 = new ContainerTemplate("jnlp", "image1");
        container1.setResourceLimitCpu("1");
        container1.setResourceLimitMemory("1Gi");
        container1.setResourceRequestCpu("100m");
        container1.setResourceRequestMemory("156Mi");
        container1.setRunAsUser(1000L);
        container1.setRunAsGroup(2000L);
        parent.setContainers(Arrays.asList(container1));

        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        template.setInheritFrom("parent");
        setupStubs();

        PodTemplate result = combine(parent, template);
        Pod pod = new PodTemplateBuilder(result).withSlave(slave).build();

        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
        assertEquals(new Quantity("1"), jnlp.getResources().getLimits().get("cpu"));
        assertEquals(new Quantity("1Gi"), jnlp.getResources().getLimits().get("memory"));
        assertEquals(new Quantity("100m"), jnlp.getResources().getRequests().get("cpu"));
        assertEquals(new Quantity("156Mi"), jnlp.getResources().getRequests().get("memory"));
        assertEquals(1000L, (Object) jnlp.getSecurityContext().getRunAsUser());
        assertEquals(2000L, (Object) jnlp.getSecurityContext().getRunAsGroup());
        validateContainers(pod, slave);
    }

    @Test
    public void yamlMergeContainers() throws Exception {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                "apiVersion: v1\n" +
                "kind: Pod\n" +
                "metadata:\n" +
                "  labels:\n" +
                "    some-label: some-label-value\n" +
                "spec:\n" +
                "  containers:\n" +
                "  - name: container1\n" +
                "    image: busybox\n" +
                "    command:\n" +
                "    - cat\n" +
                "    tty: true\n"
        );

        PodTemplate child = new PodTemplate();
        child.setYaml(
                "spec:\n" +
                "  containers:\n" +
                "  - name: container2\n" +
                "    image: busybox\n" +
                "    command:\n" +
                "    - cat\n" +
                "    tty: true\n"
        );
        child.setYamlMergeStrategy(merge());
        child.setInheritFrom("parent");
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result).withSlave(slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getContainers(), hasSize(3));
        Optional<Container> container1 = pod.getSpec().getContainers().stream().filter(c -> "container1".equals(c.getName())).findFirst();
        assertTrue(container1.isPresent());
        Optional<Container> container2 = pod.getSpec().getContainers().stream().filter(c -> "container2".equals(c.getName())).findFirst();
        assertTrue(container2.isPresent());
    }

    @Test
    public void yamlOverrideContainer() throws Exception {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                "apiVersion: v1\n" +
                "kind: Pod\n" +
                "metadata:\n" +
                "  labels:\n" +
                "    some-label: some-label-value\n" +
                "spec:\n" +
                "  containers:\n" +
                "  - name: container\n" +
                "    image: busybox\n" +
                "    command:\n" +
                "    - cat\n" +
                "    tty: true\n"
        );

        PodTemplate child = new PodTemplate();
        child.setYaml(
                "spec:\n" +
                "  containers:\n" +
                "  - name: container\n" +
                "    image: busybox2\n" +
                "    command:\n" +
                "    - cat\n" +
                "    tty: true\n"
        );
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result).withSlave(slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getContainers(), hasSize(2));
        Optional<Container> container = pod.getSpec().getContainers().stream().filter(c -> "container".equals(c.getName())).findFirst();
        assertTrue(container.isPresent());
        assertEquals("busybox2", container.get().getImage());
    }

    @Issue("JENKINS-58374")
    @Test
    public void yamlOverrideContainerEnvvar() throws Exception {
        PodTemplate parent = new PodTemplate();
        parent.setYaml("kind: Pod\n" +
                "spec:\n" +
                "  containers:\n" +
                "  - name: jnlp\n" +
                "    env:\n" +
                "    - name: VAR1\n" +
                "      value: \"1\"\n" +
                "    - name: VAR2\n" +
                "      value: \"1\"\n");
        PodTemplate child = new PodTemplate();
        child.setYamlMergeStrategy(merge());
        child.setYaml("kind: Pod\n" +
                "spec:\n" +
                "  containers:\n" +
                "  - name: jnlp\n" +
                "    env:\n" +
                "    - name: VAR1\n" +
                "      value: \"2\"\n");
        setupStubs();

        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result).withSlave(slave).build();
        Map<String, Container> containers = toContainerMap(pod);
        Container jnlp = containers.get("jnlp");
        Map<String, EnvVar> env = PodTemplateUtils.envVarstoMap(jnlp.getEnv());
        assertEquals("2", env.get("VAR1").getValue()); // value from child
        assertEquals("1", env.get("VAR2").getValue()); // value from parent
    }

    @Test
    public void yamlOverrideVolume() throws Exception {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                "apiVersion: v1\n" +
                "kind: Pod\n" +
                "metadata:\n" +
                "  labels:\n" +
                "    some-label: some-label-value\n" +
                "spec:\n" +
                "  volumes:\n" +
                "  - name: host-volume\n" +
                "    hostPath:\n" +
                "      path: /host/data\n"
        );

        PodTemplate child = new PodTemplate();
        child.setYaml(
                "spec:\n" +
                "  volumes:\n" +
                "  - name: host-volume\n" +
                "    hostPath:\n" +
                "      path: /host/data2\n"
        );
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result).withSlave(slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getVolumes(), hasSize(2));
        Optional<Volume> hostVolume = pod.getSpec().getVolumes().stream().filter(v -> "host-volume".equals(v.getName())).findFirst();
        assertTrue(hostVolume.isPresent());
        assertThat(hostVolume.get().getHostPath().getPath(), equalTo("/host/data2")); // child value overrides parent value
    }

    @Test
    public void yamlOverrideSecurityContext() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                "apiVersion: v1\n" +
                "kind: Pod\n" +
                "metadata:\n" +
                "  labels:\n" +
                "    some-label: some-label-value\n" +
                "spec:\n" +
                "  securityContext:\n" +
                "    runAsUser: 2000\n" +
                "    runAsGroup: 2000\n" +
                "  containers:\n" +
                "  - name: container\n" +
                "    securityContext:\n" +
                "      runAsUser: 1000\n" +
                "      runAsGroup: 1000\n" +
                "    image: busybox\n" +
                "    command:\n" +
                "    - cat\n" +
                "    tty: true\n"
        );

        PodTemplate child = new PodTemplate();
        child.setYaml(
                "spec:\n" +
                "  securityContext:\n" +
                "    runAsUser: 3000\n" +
                "    runAsGroup: 3000\n" +
                "  containers:\n" +
                "  - name: container\n" +
                "    image: busybox2\n" +
                "    securityContext:\n" +
                "      runAsUser: 2000\n" +
                "      runAsGroup: 2000\n" +
                "    command:\n" +
                "    - cat\n" +
                "    tty: true\n"
        );
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result).withSlave(slave).build();
        assertThat(pod.getSpec().getContainers(), hasSize(2));
        Optional<Container> container = pod.getSpec().getContainers().stream().filter(c -> "container".equals(c.getName())).findFirst();
        assertTrue(container.isPresent());
        assertEquals(3000L, (Object) pod.getSpec().getSecurityContext().getRunAsUser());
        assertEquals(3000L, (Object) pod.getSpec().getSecurityContext().getRunAsGroup());
        assertEquals(2000L, (Object) container.get().getSecurityContext().getRunAsUser());
        assertEquals(2000L, (Object) container.get().getSecurityContext().getRunAsGroup());
    }

    @Test
    public void yamlMergeVolumes() throws Exception {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                "apiVersion: v1\n" +
                "kind: Pod\n" +
                "metadata:\n" +
                "  labels:\n" +
                "    some-label: some-label-value\n" +
                "spec:\n" +
                "  volumes:\n" +
                "  - name: host-volume\n" +
                "    hostPath:\n" +
                "      path: /host/data\n"
        );

        PodTemplate child = new PodTemplate();
        child.setYaml(
                "spec:\n" +
                "  volumes:\n" +
                "  - name: host-volume2\n" +
                "    hostPath:\n" +
                "      path: /host/data2\n"
        );
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result).withSlave(slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getVolumes(), hasSize(3));
        Optional<Volume> hostVolume = pod.getSpec().getVolumes().stream().filter(v -> "host-volume".equals(v.getName())).findFirst();
        assertTrue(hostVolume.isPresent());
        assertThat(hostVolume.get().getHostPath().getPath(), equalTo("/host/data")); // parent value
        Optional<Volume> hostVolume2 = pod.getSpec().getVolumes().stream().filter(v -> "host-volume2".equals(v.getName())).findFirst();
        assertTrue(hostVolume2.isPresent());
        assertThat(hostVolume2.get().getHostPath().getPath(), equalTo("/host/data2")); // child value
    }

    @Test
    public void testOverridesContainerSpec() throws Exception {
        PodTemplate template = new PodTemplate();
        ContainerTemplate cT = new ContainerTemplate("jnlp", "jenkinsci/jnlp-slave:latest");
        template.setContainers(Lists.newArrayList(cT));
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template).withSlave(slave).build();

        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
		assertEquals("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), 1, jnlp.getVolumeMounts().size());
        validateContainers(pod, slave);
    }

    private Map<String, Container> toContainerMap(Pod pod) {
        return pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
    }

    private String loadYamlFile(String s) throws IOException {
        return new String(IOUtils.toByteArray(getClass().getResourceAsStream(s)));
    }

    private YamlMergeStrategy merge() {
        return new Merge();
    }
}
