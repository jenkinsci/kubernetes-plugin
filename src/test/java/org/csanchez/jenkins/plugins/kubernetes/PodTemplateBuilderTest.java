package org.csanchez.jenkins.plugins.kubernetes;

import io.fabric8.kubernetes.api.model.*;
import jenkins.model.Jenkins;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.naming.TestCaseName;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.*;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder.*;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.combine;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(JUnitParamsRunner.class)
public class PodTemplateBuilderTest {

    private static final String AGENT_NAME = "jenkins-agent";
    private static final String AGENT_SECRET = "xxx";
    private static final String JENKINS_URL = "http://jenkins.example.com";
    private static final String JENKINS_PROTOCOLS = "JNLP4-connect";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public LoggerRule logs = new LoggerRule().record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()),
            Level.ALL);

    @Rule
    public FlagRule<String> dockerPrefix = new FlagRule<>(() -> DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX, prefix -> DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = prefix);

    @Spy
    private KubernetesCloud cloud = new KubernetesCloud("test");

    @Mock
    private KubernetesSlave slave;

    @Mock
    private KubernetesComputer computer;

    @Before
    public void setUp() {
        when(slave.getKubernetesCloud()).thenReturn(cloud);
    }

    @WithoutJenkins
    @Test
    public void testParseDockerCommand() {
        assertNull(parseDockerCommand(""));
        assertNull(parseDockerCommand(null));
        assertEquals(Collections.singletonList("bash"), parseDockerCommand("bash"));
        assertEquals(Collections.unmodifiableList(Arrays.asList("bash", "-c", "x y")), parseDockerCommand("bash -c \"x y\""));
        assertEquals(Collections.unmodifiableList(Arrays.asList("a", "b", "c", "d")), parseDockerCommand("a b c d"));
    }

    @WithoutJenkins
    @Test
    public void testParseLivenessProbe() {
        assertNull(parseLivenessProbe(""));
        assertNull(parseLivenessProbe(null));
        assertEquals(Collections.unmodifiableList(Arrays.asList("docker", "info")), parseLivenessProbe("docker info"));
        assertEquals(Collections.unmodifiableList(Arrays.asList("echo", "I said: 'I am alive'")),
                parseLivenessProbe("echo \"I said: 'I am alive'\""));
        assertEquals(Collections.unmodifiableList(Arrays.asList("docker", "--version")), parseLivenessProbe("docker --version"));
        assertEquals(Collections.unmodifiableList(Arrays.asList("curl", "-k", "--silent", "--output=/dev/null", "https://localhost:8080")),
                parseLivenessProbe("curl -k --silent --output=/dev/null \"https://localhost:8080\""));
    }

    @Test
    @TestCaseName("{method}(directConnection={0})")
    @Parameters({ "true", "false" })
    public void testBuildFromYaml(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        validatePod(pod, directConnection);
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
    @TestCaseName("{method}(directConnection={0})")
    @Parameters({ "true", "false" })
    public void testValidateDockerRegistryPrefixOverride(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = "jenkins.docker.com/docker-hub";
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        // check containers
        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(2, containers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals(DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX + "/" + DEFAULT_JNLP_IMAGE, containers.get("jnlp").getImage());
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
    }

    @Test
    @TestCaseName("{method}(directConnection={0})")
    @Parameters({ "true", "false" })
    public void testValidateDockerRegistryPrefixOverrideWithSlashSuffix(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = "jenkins.docker.com/docker-hub/";
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        // check containers
        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(2, containers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals(DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX + DEFAULT_JNLP_IMAGE, containers.get("jnlp").getImage());
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
    }

    @Test
    @Issue("JENKINS-50525")
    public void testBuildWithCustomWorkspaceVolume() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setWorkspaceVolume(new EmptyDirWorkspaceVolume(true));
        ContainerTemplate containerTemplate = new ContainerTemplate("name", "image");
        containerTemplate.setWorkingDir("");
        template.getContainers().add(containerTemplate);
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        List<Container> containers = pod.getSpec().getContainers();
        assertEquals(2, containers.size());
        Container container0 = containers.get(0);
        Container container1 = containers.get(1);

        List<VolumeMount> volumeMounts = Collections.singletonList(new VolumeMountBuilder()
                .withMountPath("/home/jenkins/agent").withName("workspace-volume").withReadOnly(false).build());

        assertEquals(volumeMounts, container0.getVolumeMounts());
        assertEquals(volumeMounts, container1.getVolumeMounts());
        assertEquals("Memory", pod.getSpec().getVolumes().get(0).getEmptyDir().getMedium());
    }

    @Test
    public void testBuildWithDynamicPVCWorkspaceVolume() {
        PodTemplate template = new PodTemplate();
        template.setWorkspaceVolume(new DynamicPVCWorkspaceVolume(
                null, null,null));
        ContainerTemplate containerTemplate = new ContainerTemplate("name", "image");
        containerTemplate.setWorkingDir("");
        template.getContainers().add(containerTemplate);
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        List<Container> containers = pod.getSpec().getContainers();
        assertEquals(2, containers.size());
        Container container0 = containers.get(0);
        Container container1 = containers.get(1);
        List<VolumeMount> volumeMounts = Collections.singletonList(new VolumeMountBuilder()
                .withMountPath("/home/jenkins/agent").withName("workspace-volume").withReadOnly(false).build());

        assertEquals(volumeMounts, container0.getVolumeMounts());
        assertEquals(volumeMounts, container1.getVolumeMounts());
        assertNotNull(pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim());
    }

    @Test
    @TestCaseName("{method}(directConnection={0})")
    @Parameters({ "true", "false" })
    public void testBuildFromTemplate(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate template = new PodTemplate();
        template.setRunAsUser("1000");
        template.setRunAsGroup("1000");
        template.setSupplementalGroups("5001,5002");

        template.setHostNetwork(false);

        List<PodVolume> volumes = new ArrayList<PodVolume>();
        volumes.add(new HostPathVolume("/host/data", "/container/data", null));
        volumes.add(new EmptyDirVolume("/empty/dir", false, null));
        template.setVolumes(volumes);

        List<ContainerTemplate> containers = new ArrayList<ContainerTemplate>();
        ContainerTemplate busyboxContainer = new ContainerTemplate("busybox", "busybox");
        busyboxContainer.setCommand("cat");
        busyboxContainer.setTtyEnabled(true);
        List<TemplateEnvVar> envVars = new ArrayList<TemplateEnvVar>();
        envVars.add(new KeyValueEnvVar("CONTAINER_ENV_VAR", "container-env-var-value"));
        busyboxContainer.setEnvVars(envVars);
        busyboxContainer.setRunAsUser("2000");
        busyboxContainer.setRunAsGroup("2000");
        containers.add(busyboxContainer);
        template.setContainers(containers);

        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        pod.getMetadata().setLabels(Collections.singletonMap("some-label", "some-label-value"));
        validatePod(pod, false, directConnection);
        ArrayList<Long> supplementalGroups = new ArrayList<Long>();
        supplementalGroups.add(5001L);
        supplementalGroups.add(5002L);

        Map<String, Container> containersMap = toContainerMap(pod);
        PodSecurityContext securityContext = pod.getSpec().getSecurityContext();
        assertEquals(Long.valueOf(1000L), securityContext.getRunAsUser());
        assertEquals(Long.valueOf(1000L), securityContext.getRunAsGroup());
        assertEquals(supplementalGroups, securityContext.getSupplementalGroups());
        assertEquals(Long.valueOf(2000L), containersMap.get("busybox").getSecurityContext().getRunAsUser());
        assertEquals(Long.valueOf(2000L), containersMap.get("busybox").getSecurityContext().getRunAsGroup());
    }

    private void setupStubs() {
        doReturn(JENKINS_URL).when(cloud).getJenkinsUrlOrDie();
        when(computer.getName()).thenReturn(AGENT_NAME);
        when(computer.getJnlpMac()).thenReturn(AGENT_SECRET);
        when(slave.getComputer()).thenReturn(computer);
        when(slave.getKubernetesCloud()).thenReturn(cloud);
    }

    private void validatePod(Pod pod, boolean directConnection) {
        validatePod(pod, true, directConnection);
    }

    private void validatePod(Pod pod, boolean fromYaml, boolean directConnection) {
        assertThat(pod.getMetadata().getLabels(), hasEntry("some-label", "some-label-value"));
        assertEquals("Never", pod.getSpec().getRestartPolicy());

        // check containers
        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(2, containers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals(DEFAULT_JNLP_IMAGE, containers.get("jnlp").getImage());

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

        validateContainers(pod, slave, directConnection);
    }

    private void validateContainers(Pod pod, KubernetesSlave slave, boolean directConnection) {
        String[] exclusions = new String[] {"JENKINS_URL", "JENKINS_SECRET", "JENKINS_NAME", "JENKINS_AGENT_NAME", "JENKINS_AGENT_WORKDIR"};
        for (Container c : pod.getSpec().getContainers()) {
            if ("jnlp".equals(c.getName())) {
                validateJnlpContainer(c, slave, directConnection);
            } else {
                List<EnvVar> env = c.getEnv();
                assertThat(env.stream().map(EnvVar::getName).collect(toList()), everyItem(not(isIn(exclusions))));
            }
        }
    }

    private void validateJnlpContainer(Container jnlp, KubernetesSlave slave, boolean directConnection) {
        assertThat(jnlp.getCommand(), empty());
        List<EnvVar> envVars = new ArrayList<>();
        if (slave != null) {
            assertThat(jnlp.getArgs(), empty());
            if(directConnection) {
              envVars.add(new EnvVar("JENKINS_PROTOCOLS", JENKINS_PROTOCOLS, null));
              envVars.add(new EnvVar("JENKINS_DIRECT_CONNECTION", "localhost:" + Jenkins.get().getTcpSlaveAgentListener().getAdvertisedPort(), null));
              envVars.add(new EnvVar("JENKINS_INSTANCE_IDENTITY", Jenkins.get().getTcpSlaveAgentListener().getIdentityPublicKey(), null));
            } else {
              envVars.add(new EnvVar("JENKINS_URL", JENKINS_URL, null));
            }
            envVars.add(new EnvVar("JENKINS_SECRET", AGENT_SECRET, null));
            envVars.add(new EnvVar("JENKINS_NAME", AGENT_NAME, null));
            envVars.add(new EnvVar("JENKINS_AGENT_NAME", AGENT_NAME, null));
            envVars.add(new EnvVar("JENKINS_AGENT_WORKDIR", ContainerTemplate.DEFAULT_WORKING_DIR, null));
        } else {
            assertThat(jnlp.getArgs(), empty());
        }
        assertThat(jnlp.getEnv(), containsInAnyOrder(envVars.toArray(new EnvVar[envVars.size()])));
    }

    @Test
    public void defaultRequests() throws Exception {
        PodTemplate template = new PodTemplate();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        ResourceRequirements resources = pod.getSpec().getContainers().get(0).getResources();
        assertNotNull(resources);
        Map<String, Quantity> requests = resources.getRequests();
        assertNotNull(requests);
        PodTemplateUtilsTest.assertQuantity(PodTemplateBuilder.DEFAULT_JNLP_CONTAINER_CPU_REQUEST, requests.get("cpu"));
        PodTemplateUtilsTest.assertQuantity(PodTemplateBuilder.DEFAULT_JNLP_CONTAINER_MEMORY_REQUEST, requests.get("memory"));
    }

    @Test
    @TestCaseName("{method}(directConnection={0})")
    @Parameters({ "true", "false" })
    public void testOverridesFromYaml(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();

        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
        assertThat("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), jnlp.getVolumeMounts(), hasSize(1));
        PodTemplateUtilsTest.assertQuantity("2", jnlp.getResources().getLimits().get("cpu"));
        PodTemplateUtilsTest.assertQuantity("2Gi", jnlp.getResources().getLimits().get("memory"));
        PodTemplateUtilsTest.assertQuantity("200m", jnlp.getResources().getRequests().get("cpu"));
        PodTemplateUtilsTest.assertQuantity("256Mi", jnlp.getResources().getRequests().get("memory"));
        validateContainers(pod, slave, directConnection);
    }

    /**
     * This is counter intuitive, the yaml contents are ignored because the parent fields are merged first with the
     * child ones. Then the fields override what is defined in the yaml, so in effect the parent resource limits and
     * requests are used.
     */
    @Test
    @TestCaseName("{method}(directConnection={0})")
    @Parameters({ "true", "false" })
    public void testInheritsFromWithYaml(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate parent = new PodTemplate();
        ContainerTemplate container1 = new ContainerTemplate("jnlp", "image1");
        container1.setResourceLimitCpu("1");
        container1.setResourceLimitMemory("1Gi");
        container1.setResourceRequestCpu("100m");
        container1.setResourceRequestMemory("156Mi");
        container1.setRunAsUser("1000");
        container1.setRunAsGroup("2000");
        parent.setContainers(Arrays.asList(container1));

        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        template.setInheritFrom("parent");
        setupStubs();

        PodTemplate result = combine(parent, template);
        Pod pod = new PodTemplateBuilder(result, slave).build();

        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
        PodTemplateUtilsTest.assertQuantity("1", jnlp.getResources().getLimits().get("cpu"));
        PodTemplateUtilsTest.assertQuantity("1Gi", jnlp.getResources().getLimits().get("memory"));
        PodTemplateUtilsTest.assertQuantity("100m", jnlp.getResources().getRequests().get("cpu"));
        PodTemplateUtilsTest.assertQuantity("156Mi", jnlp.getResources().getRequests().get("memory"));
        assertEquals(Long.valueOf(1000L), jnlp.getSecurityContext().getRunAsUser());
        assertEquals(Long.valueOf(2000L), jnlp.getSecurityContext().getRunAsGroup());
        validateContainers(pod, slave, directConnection);
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
        Pod pod = new PodTemplateBuilder(result, slave).build();
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
        Pod pod = new PodTemplateBuilder(result, slave).build();
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
        Pod pod = new PodTemplateBuilder(result, slave).build();
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
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getVolumes(), hasSize(2));
        Optional<Volume> hostVolume = pod.getSpec().getVolumes().stream().filter(v -> "host-volume".equals(v.getName())).findFirst();
        assertTrue(hostVolume.isPresent());
        assertThat(hostVolume.get().getHostPath().getPath(), equalTo("/host/data2")); // child value overrides parent value
    }

    @Test
    public void yamlOverrideHostNetwork() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                "apiVersion: v1\n" +
                "kind: Pod\n" +
                "metadata:\n" +
                "  labels:\n" +
                "    some-label: some-label-value\n" +
                "spec:\n" +
                "  hostNetwork: false\n" +
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
                "  hostNetwork: true\n" +
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
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertTrue(pod.getSpec().getHostNetwork());
    }

    @Test
    public void yamlOverrideSchedulerName() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                "apiVersion: v1\n" +
                "kind: Pod\n" +
                "metadata:\n" +
                "  labels:\n" +
                "    some-label: some-label-value\n" +
                "spec:\n" +
                "  schedulerName: default-scheduler\n"
        );

        PodTemplate child = new PodTemplate();
        child.setYaml(
                "spec:\n" +
                "  schedulerName: custom-scheduler\n"
        );
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertEquals("custom-scheduler", pod.getSpec().getSchedulerName());
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
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertThat(pod.getSpec().getContainers(), hasSize(2));
        Optional<Container> container = pod.getSpec().getContainers().stream().filter(c -> "container".equals(c.getName())).findFirst();
        assertTrue(container.isPresent());
        assertEquals(Long.valueOf(3000L), pod.getSpec().getSecurityContext().getRunAsUser());
        assertEquals(Long.valueOf(3000L), pod.getSpec().getSecurityContext().getRunAsGroup());
        assertEquals(Long.valueOf(2000L), container.get().getSecurityContext().getRunAsUser());
        assertEquals(Long.valueOf(2000L), container.get().getSecurityContext().getRunAsGroup());
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
        Pod pod = new PodTemplateBuilder(result, slave).build();
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
    @TestCaseName("{method}(directConnection={0})")
    @Parameters({ "true", "false" })
    public void testOverridesContainerSpec(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate template = new PodTemplate();
        ContainerTemplate cT = new ContainerTemplate("jnlp", "jenkinsci/jnlp-slave:latest");
        template.setContainers(Arrays.asList(cT));
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();

        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
		assertEquals("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), 1, jnlp.getVolumeMounts().size());
        validateContainers(pod, slave, directConnection);
    }

    @Test
    public void whenRuntimeClassNameIsSetDoNotSetDefaultNodeSelector() {
        setupStubs();
        PodTemplate template = new PodTemplate();
        template.setYaml("spec:\n" +
                "  runtimeClassName: windows");
        Pod pod = new PodTemplateBuilder(template, slave).build();
        assertEquals("windows", pod.getSpec().getRuntimeClassName());
        assertThat(pod.getSpec().getNodeSelector(), anEmptyMap());
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
