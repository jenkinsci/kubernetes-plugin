package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder.*;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Overrides;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.YamlMergeStrategy;
import org.csanchez.jenkins.plugins.kubernetes.volumes.ConfigMapVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.EmptyDirWorkspaceVolume;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@WithJenkins
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PodTemplateBuilderTest {

    private static final String AGENT_NAME = "jenkins-agent";
    private static final String AGENT_SECRET = "xxx";
    private static final String JENKINS_URL = "http://jenkins.example.com";
    private static final String JENKINS_PROTOCOLS = "JNLP4-connect";

    private JenkinsRule r;

    @SuppressWarnings("unused")
    private final LogRecorder logs = new LogRecorder()
            .record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()), Level.ALL);

    private String dockerPrefix;

    @Spy
    private KubernetesCloud cloud = new KubernetesCloud("test");

    @Mock
    private KubernetesSlave slave;

    @Mock
    private KubernetesComputer computer;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
        when(slave.getKubernetesCloud()).thenReturn(cloud);
        dockerPrefix = DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX;
    }

    @AfterEach
    void afterEach() {
        DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = dockerPrefix;
    }

    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testBuildFromYaml(boolean directConnection) throws Exception {
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
    void testBuildJnlpFromYamlWithNullEnv() throws Exception {
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-jnlp-nullenv.yaml"));
        Pod pod = new PodTemplateBuilder(template, slave).build();
        Optional<Container> jnlp = pod.getSpec().getContainers().stream()
                .filter(c -> KubernetesCloud.JNLP_NAME.equals(c.getName()))
                .findFirst();
        assertThat("jnlp container is present", jnlp.isPresent(), is(true));
        assertThat(jnlp.get().getEnv(), hasSize(greaterThan(0)));
    }

    @Test
    @Issue("JENKINS-71639")
    void testInjectRestrictedPssSecurityContextInJnlp() throws Exception {
        cloud.setRestrictedPssSecurityContext(true);
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        Map<String, Container> containers = toContainerMap(pod);
        assertTrue(containers.containsKey("jnlp"));

        Container jnlp = containers.get("jnlp");
        assertNotNull(jnlp.getSecurityContext());
        assertFalse(jnlp.getSecurityContext().getAllowPrivilegeEscalation());
        assertNotNull(jnlp.getSecurityContext().getCapabilities());
        assertNotNull(jnlp.getSecurityContext().getCapabilities().getDrop());
        assertTrue(jnlp.getSecurityContext().getCapabilities().getDrop().contains("ALL"));
        assertTrue(jnlp.getSecurityContext().getRunAsNonRoot());
        assertNotNull(jnlp.getSecurityContext().getSeccompProfile());
        assertEquals(
                "RuntimeDefault", jnlp.getSecurityContext().getSeccompProfile().getType());
    }

    @Test
    void testValidateDockerRegistryUIOverride() throws Exception {
        final String jnlpregistry = "registry.example.com";
        cloud.setJnlpregistry(jnlpregistry);
        DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = "jenkins.docker.com/docker-hub"; // should be ignored
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        // check containers
        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(2, containers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals(
                jnlpregistry + "/" + DEFAULT_AGENT_IMAGE, containers.get("jnlp").getImage());
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
    }

    @Test
    void testValidateDockerRegistryUIOverrideWithSlashSuffix() throws Exception {
        final String jnlpregistry = "registry.example.com/";
        cloud.setJnlpregistry(jnlpregistry);
        DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = "jenkins.docker.com/docker-hub"; // should be ignored
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        // check containers
        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(2, containers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals(jnlpregistry + DEFAULT_AGENT_IMAGE, containers.get("jnlp").getImage());
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
    }

    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testValidateDockerRegistryPrefixOverride(boolean directConnection) throws Exception {
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
        assertEquals(
                DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX + "/" + DEFAULT_AGENT_IMAGE,
                containers.get("jnlp").getImage());
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
    }

    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testValidateDockerRegistryPrefixOverrideWithSlashSuffix(boolean directConnection) throws Exception {
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
        assertEquals(
                DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX + DEFAULT_AGENT_IMAGE,
                containers.get("jnlp").getImage());
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
    }

    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testValidateDockerRegistryPrefixOverrideForInitContainer(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = "jenkins.docker.com/docker-hub";
        PodTemplate template = new PodTemplate();
        template.setYaml(loadYamlFile("pod-busybox.yaml"));
        template.setAgentContainer("busybox");
        template.setAgentInjection(true);
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        // check containers
        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Map<String, Container> initContainers = toInitContainerMap(pod);
        assertEquals(1, initContainers.size());

        assertEquals("busybox", containers.get("busybox").getImage());
        assertEquals(
                List.of("/jenkins-agent/jenkins-agent"),
                containers.get("busybox").getCommand());
        assertEquals(
                DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX + "/" + DEFAULT_AGENT_IMAGE,
                initContainers.get("set-up-jenkins-agent").getImage());
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
    }

    @Test
    @Issue("JENKINS-50525")
    void testBuildWithCustomWorkspaceVolume() {
        PodTemplate template = new PodTemplate();
        var workspaceVolume = new EmptyDirWorkspaceVolume(true);
        workspaceVolume.setSizeLimit("1Gi");
        template.setWorkspaceVolume(workspaceVolume);
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
                .withMountPath("/home/jenkins/agent")
                .withName("workspace-volume")
                .withReadOnly(false)
                .build());

        assertEquals(volumeMounts, container0.getVolumeMounts());
        assertEquals(volumeMounts, container1.getVolumeMounts());
        var emptyDirVolumeSource = pod.getSpec().getVolumes().get(0).getEmptyDir();
        assertEquals("Memory", emptyDirVolumeSource.getMedium());
        assertThat(emptyDirVolumeSource.getSizeLimit(), is(new Quantity("1Gi")));
    }

    @Test
    void testBuildWithDynamicPVCWorkspaceVolume() {
        PodTemplate template = new PodTemplate();
        template.setWorkspaceVolume(new DynamicPVCWorkspaceVolume());
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
                .withMountPath("/home/jenkins/agent")
                .withName("workspace-volume")
                .withReadOnly(false)
                .build());

        assertEquals(volumeMounts, container0.getVolumeMounts());
        assertEquals(volumeMounts, container1.getVolumeMounts());
        assertNotNull(pod.getSpec().getVolumes().get(0).getPersistentVolumeClaim());
    }

    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testBuildFromTemplate(boolean directConnection) {
        cloud.setDirectConnection(directConnection);
        PodTemplate template = new PodTemplate();
        template.setRunAsUser("1000");
        template.setRunAsGroup("1000");
        template.setSupplementalGroups("5001,5002");

        template.setHostNetwork(false);

        List<PodVolume> volumes = new ArrayList<>();
        volumes.add(new HostPathVolume("/host/data", "/container/data", false));
        volumes.add(new EmptyDirVolume("/empty/dir", false));
        template.setVolumes(volumes);

        List<ContainerTemplate> containers = new ArrayList<>();
        ContainerTemplate busyboxContainer = new ContainerTemplate("busybox", "busybox");
        busyboxContainer.setCommand("cat");
        busyboxContainer.setTtyEnabled(true);
        List<TemplateEnvVar> envVars = new ArrayList<>();
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
        ArrayList<Long> supplementalGroups = new ArrayList<>();
        supplementalGroups.add(5001L);
        supplementalGroups.add(5002L);

        Map<String, Container> containersMap = toContainerMap(pod);
        PodSecurityContext securityContext = pod.getSpec().getSecurityContext();
        assertEquals(Long.valueOf(1000L), securityContext.getRunAsUser());
        assertEquals(Long.valueOf(1000L), securityContext.getRunAsGroup());
        assertEquals(supplementalGroups, securityContext.getSupplementalGroups());
        assertEquals(
                Long.valueOf(2000L),
                containersMap.get("busybox").getSecurityContext().getRunAsUser());
        assertEquals(
                Long.valueOf(2000L),
                containersMap.get("busybox").getSecurityContext().getRunAsGroup());
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
        assertEquals(DEFAULT_AGENT_IMAGE, containers.get("jnlp").getImage());

        // check volumes and volume mounts
        Map<String, Volume> volumes =
                pod.getSpec().getVolumes().stream().collect(Collectors.toMap(Volume::getName, Function.identity()));
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
                .withMountPath("/home/jenkins/agent")
                .withName("workspace-volume")
                .withReadOnly(false)
                .build();

        // when using yaml we don't mount all volumes, just the ones explicitly listed
        if (fromYaml) {
            assertThat(
                    mounts,
                    containsInAnyOrder(
                            workspaceVolume, //
                            new VolumeMountBuilder()
                                    .withMountPath("/container/data")
                                    .withName("host-volume")
                                    .build()));
            assertThat(jnlpMounts, containsInAnyOrder(workspaceVolume));
        } else {
            List<Matcher<? super VolumeMount>> volumeMounts = Arrays.asList( //
                    equalTo(workspaceVolume), //
                    equalTo(
                            new VolumeMountBuilder() //
                                    .withMountPath("/container/data")
                                    .withName("volume-0")
                                    .withReadOnly(false)
                                    .build()),
                    equalTo(
                            new VolumeMountBuilder() //
                                    .withMountPath("/empty/dir")
                                    .withName("volume-1")
                                    .withReadOnly(false)
                                    .build()));
            assertThat(mounts, containsInAnyOrder(volumeMounts));
            assertThat(jnlpMounts, containsInAnyOrder(volumeMounts));
        }

        validateContainers(pod, slave, directConnection);
    }

    private void validateContainers(Pod pod, KubernetesSlave slave, boolean directConnection) {
        String[] exclusions = new String[] {
            "JENKINS_URL", "JENKINS_SECRET", "JENKINS_NAME", "JENKINS_AGENT_NAME", "JENKINS_AGENT_WORKDIR"
        };
        for (Container c : pod.getSpec().getContainers()) {
            if ("jnlp".equals(c.getName())) {
                validateJnlpContainer(c, slave, directConnection);
            } else {
                List<EnvVar> env = c.getEnv();
                assertThat(env.stream().map(EnvVar::getName).toList(), everyItem(not(is(in(exclusions)))));
            }
        }
    }

    private void validateJnlpContainer(Container jnlp, KubernetesSlave slave, boolean directConnection) {
        assertThat(jnlp.getCommand(), empty());
        List<EnvVar> envVars = new ArrayList<>();
        if (slave != null) {
            assertThat(jnlp.getArgs(), empty());
            if (directConnection) {
                envVars.add(new EnvVar("JENKINS_PROTOCOLS", JENKINS_PROTOCOLS, null));
                envVars.add(new EnvVar(
                        "JENKINS_DIRECT_CONNECTION",
                        System.getProperty("hudson.TcpSlaveAgentListener.hostName", "localhost") + ":"
                                + Jenkins.get().getTcpSlaveAgentListener().getAdvertisedPort(),
                        null));
                envVars.add(new EnvVar(
                        "JENKINS_INSTANCE_IDENTITY",
                        Jenkins.get().getTcpSlaveAgentListener().getIdentityPublicKey(),
                        null));
            } else {
                envVars.add(new EnvVar("JENKINS_URL", JENKINS_URL, null));
            }
            envVars.add(new EnvVar("JENKINS_SECRET", AGENT_SECRET, null));
            envVars.add(new EnvVar("JENKINS_NAME", AGENT_NAME, null));
            envVars.add(new EnvVar("JENKINS_AGENT_NAME", AGENT_NAME, null));
            envVars.add(new EnvVar("JENKINS_AGENT_WORKDIR", ContainerTemplate.DEFAULT_WORKING_DIR, null));
            envVars.add(new EnvVar("REMOTING_OPTS", "-noReconnectAfter " + NO_RECONNECT_AFTER_TIMEOUT, null));
        } else {
            assertThat(jnlp.getArgs(), empty());
        }
        assertThat(jnlp.getEnv(), containsInAnyOrder(envVars.toArray(new EnvVar[0])));
    }

    @Test
    void namespaceFromCloud() {
        when(cloud.getNamespace()).thenReturn("cloud-namespace");
        PodTemplate template = new PodTemplate();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        assertEquals("cloud-namespace", pod.getMetadata().getNamespace());
    }

    @Test
    void namespaceFromTemplate() {
        when(cloud.getNamespace()).thenReturn("cloud-namespace");
        PodTemplate template = new PodTemplate();
        template.setNamespace("template-namespace");
        Pod pod = new PodTemplateBuilder(template, slave).build();
        assertEquals("template-namespace", pod.getMetadata().getNamespace());
    }

    @Test
    void defaultRequests() {
        PodTemplate template = new PodTemplate();
        Pod pod = new PodTemplateBuilder(template, slave).build();
        ResourceRequirements resources = pod.getSpec().getContainers().get(0).getResources();
        assertNotNull(resources);
        Map<String, Quantity> requests = resources.getRequests();
        assertNotNull(requests);
        PodTemplateUtilsTest.assertQuantity(PodTemplateBuilder.DEFAULT_JNLP_CONTAINER_CPU_REQUEST, requests.get("cpu"));
        PodTemplateUtilsTest.assertQuantity(
                PodTemplateBuilder.DEFAULT_JNLP_CONTAINER_MEMORY_REQUEST, requests.get("memory"));
    }

    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testOverridesFromYaml(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate template = new PodTemplate();
        template.setNamespace("template-namespace");
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();

        assertEquals("yaml-namespace", pod.getMetadata().getNamespace());
        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
        assertThat("Wrong number of volume mounts: " + jnlp.getVolumeMounts(), jnlp.getVolumeMounts(), hasSize(1));
        PodTemplateUtilsTest.assertQuantity("2", jnlp.getResources().getLimits().get("cpu"));
        PodTemplateUtilsTest.assertQuantity(
                "2Gi", jnlp.getResources().getLimits().get("memory"));
        PodTemplateUtilsTest.assertQuantity(
                "200m", jnlp.getResources().getRequests().get("cpu"));
        PodTemplateUtilsTest.assertQuantity(
                "256Mi", jnlp.getResources().getRequests().get("memory"));
        validateContainers(pod, slave, directConnection);
    }

    /**
     * This is counter intuitive, the yaml contents are ignored because the parent fields are merged first with the
     * child ones. Then the fields override what is defined in the yaml, so in effect the parent resource limits and
     * requests are used.
     */
    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testInheritsFromWithYaml(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate parent = new PodTemplate();
        ContainerTemplate container1 = new ContainerTemplate("jnlp", "image1");
        container1.setResourceLimitCpu("1");
        container1.setResourceLimitMemory("1Gi");
        container1.setResourceRequestCpu("100m");
        container1.setResourceRequestMemory("156Mi");
        container1.setRunAsUser("1000");
        container1.setRunAsGroup("2000");
        parent.setContainers(List.of(container1));

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
        PodTemplateUtilsTest.assertQuantity(
                "1Gi", jnlp.getResources().getLimits().get("memory"));
        PodTemplateUtilsTest.assertQuantity(
                "100m", jnlp.getResources().getRequests().get("cpu"));
        PodTemplateUtilsTest.assertQuantity(
                "156Mi", jnlp.getResources().getRequests().get("memory"));
        assertEquals(Long.valueOf(1000L), jnlp.getSecurityContext().getRunAsUser());
        assertEquals(Long.valueOf(2000L), jnlp.getSecurityContext().getRunAsGroup());
        validateContainers(pod, slave, directConnection);
    }

    @Test
    void inheritYamlMergeStrategy() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                spec:
                  tolerations:
                  - key: "reservedFor"
                    operator: Exists
                    effect: NoSchedule""");

        PodTemplate child = new PodTemplate();
        child.setYaml("spec:\n");
        child.setInheritFrom("parent");
        setupStubs();

        PodTemplate result;
        Pod pod;

        // Default behavior (backward compatible)
        parent.setYamlMergeStrategy(merge());
        parent.setInheritYamlMergeStrategy(false);
        result = combine(parent, child);
        pod = new PodTemplateBuilder(result, slave).build();
        assertThat(pod.getSpec().getTolerations(), hasSize(0));

        // Inherit merge strategy with merge
        parent.setYamlMergeStrategy(merge());
        parent.setInheritYamlMergeStrategy(true);
        result = combine(parent, child);
        pod = new PodTemplateBuilder(result, slave).build();
        assertThat(pod.getSpec().getTolerations(), hasSize(1));

        // Inherit merge strategy with override
        parent.setYamlMergeStrategy(overrides());
        parent.setInheritYamlMergeStrategy(true);
        result = combine(parent, child);
        pod = new PodTemplateBuilder(result, slave).build();
        assertThat(pod.getSpec().getTolerations(), hasSize(0));

        // Override merge strategy with overrides
        parent.setYamlMergeStrategy(merge());
        parent.setInheritYamlMergeStrategy(true);
        child.setYamlMergeStrategy(overrides());
        result = combine(parent, child);
        pod = new PodTemplateBuilder(result, slave).build();
        assertThat(pod.getSpec().getTolerations(), hasSize(0));

        // Override overrides strategy with merge
        parent.setYamlMergeStrategy(overrides());
        parent.setInheritYamlMergeStrategy(true);
        child.setYamlMergeStrategy(merge());
        result = combine(parent, child);
        pod = new PodTemplateBuilder(result, slave).build();
        assertThat(pod.getSpec().getTolerations(), hasSize(1));
    }

    @Test
    void yamlMergeContainers() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-label-value
                spec:
                  containers:
                  - name: container1
                    image: busybox
                    command:
                    - cat
                    tty: true
                """);

        PodTemplate child = new PodTemplate();
        child.setYaml(
                """
                spec:
                  containers:
                  - name: container2
                    image: busybox
                    command:
                    - cat
                    tty: true
                """);
        child.setYamlMergeStrategy(merge());
        child.setInheritFrom("parent");
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getContainers(), hasSize(3));
        Optional<Container> container1 = pod.getSpec().getContainers().stream()
                .filter(c -> "container1".equals(c.getName()))
                .findFirst();
        assertTrue(container1.isPresent());
        Optional<Container> container2 = pod.getSpec().getContainers().stream()
                .filter(c -> "container2".equals(c.getName()))
                .findFirst();
        assertTrue(container2.isPresent());
    }

    @Test
    void yamlOverrideContainer() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-label-value
                spec:
                  containers:
                  - name: container
                    image: busybox
                    command:
                    - cat
                    tty: true
                """);

        PodTemplate child = new PodTemplate();
        child.setYaml(
                """
                spec:
                  containers:
                  - name: container
                    image: busybox2
                    command:
                    - cat
                    tty: true
                """);
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getContainers(), hasSize(2));
        Optional<Container> container = pod.getSpec().getContainers().stream()
                .filter(c -> "container".equals(c.getName()))
                .findFirst();
        assertTrue(container.isPresent());
        assertEquals("busybox2", container.get().getImage());
    }

    @Issue("JENKINS-58374")
    @Test
    void yamlOverrideContainerEnvvar() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                kind: Pod
                spec:
                  containers:
                  - name: jnlp
                    env:
                    - name: VAR1
                      value: "1"
                    - name: VAR2
                      value: "1"
                """);
        PodTemplate child = new PodTemplate();
        child.setYamlMergeStrategy(merge());
        child.setYaml(
                """
                kind: Pod
                spec:
                  containers:
                  - name: jnlp
                    env:
                    - name: VAR1
                      value: "2"
                """);
        setupStubs();

        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        Map<String, Container> containers = toContainerMap(pod);
        Container jnlp = containers.get("jnlp");
        assertThat(
                jnlp.getEnv(),
                hasItems(
                        new EnvVar("VAR1", "2", null), // value from child
                        new EnvVar("VAR2", "1", null) // value from parent
                        ));
    }

    @Test
    void yamlOverrideVolume() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-label-value
                spec:
                  containers:
                  - name: jnlp
                    volumeMounts:
                    - name: host-volume
                      mountPath: /etc/config
                      subPath: mypath
                  volumes:
                  - name: host-volume
                    hostPath:
                      path: /host/data
                """);

        PodTemplate child = new PodTemplate();
        child.setYaml(
                """
                spec:
                  volumes:
                  - name: host-volume
                    hostPath:
                      path: /host/data2
                """);
        child.setContainers(Collections.singletonList(new ContainerTemplate("jnlp", "image")));
        ConfigMapVolume cmVolume = new ConfigMapVolume("/etc/configmap", "my-configmap", false);
        cmVolume.setSubPath("subpath");
        child.setVolumes(Collections.singletonList(cmVolume));
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        Optional<Volume> maybeVolume = pod.getSpec().getVolumes().stream()
                .filter(v -> "host-volume".equals(v.getName()))
                .findFirst();
        assertTrue(maybeVolume.isPresent());
        assertThat(
                maybeVolume.get().getHostPath().getPath(),
                equalTo("/host/data2")); // child value overrides parent value
        assertThat(pod.getSpec().getContainers(), hasSize(1));
        Container container = pod.getSpec().getContainers().get(0);
        Optional<VolumeMount> maybeVolumeMount = container.getVolumeMounts().stream()
                .filter(vm -> "host-volume".equals(vm.getName()))
                .findFirst();
        assertTrue(maybeVolumeMount.isPresent());
        VolumeMount volumeMount = maybeVolumeMount.get();
        assertEquals("/etc/config", volumeMount.getMountPath());
        assertEquals("mypath", volumeMount.getSubPath());
        Optional<VolumeMount> maybeVolumeMountCm = container.getVolumeMounts().stream()
                .filter(vm -> "/etc/configmap".equals(vm.getMountPath()))
                .findFirst();
        assertTrue(maybeVolumeMountCm.isPresent());
        VolumeMount cmVolumeMount = maybeVolumeMountCm.get();
        assertEquals("subpath", cmVolumeMount.getSubPath());
    }

    @Test
    void yamlOverrideHostNetwork() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-label-value
                spec:
                  hostNetwork: false
                  containers:
                  - name: container
                    securityContext:
                      runAsUser: 1000
                      runAsGroup: 1000
                    image: busybox
                    command:
                    - cat
                    tty: true
                """);

        PodTemplate child = new PodTemplate();
        child.setYaml(
                """
                spec:
                  hostNetwork: true
                  containers:
                  - name: container
                    image: busybox2
                    securityContext:
                      runAsUser: 2000
                      runAsGroup: 2000
                    command:
                    - cat
                    tty: true
                """);
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertTrue(pod.getSpec().getHostNetwork());
    }

    @Test
    void yamlOverrideSchedulerName() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-label-value
                spec:
                  schedulerName: default-scheduler
                """);

        PodTemplate child = new PodTemplate();
        child.setYaml("""
                spec:
                  schedulerName: custom-scheduler
                """);
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertEquals("custom-scheduler", pod.getSpec().getSchedulerName());
    }

    @Test
    void yamlOverrideSecurityContext() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-label-value
                spec:
                  securityContext:
                    runAsUser: 2000
                    runAsGroup: 2000
                  containers:
                  - name: container
                    securityContext:
                      runAsUser: 1000
                      runAsGroup: 1000
                    image: busybox
                    command:
                    - cat
                    tty: true
                """);

        PodTemplate child = new PodTemplate();
        child.setYaml(
                """
                spec:
                  securityContext:
                    runAsUser: 3000
                    runAsGroup: 3000
                  containers:
                  - name: container
                    image: busybox2
                    securityContext:
                      runAsUser: 2000
                      runAsGroup: 2000
                    command:
                    - cat
                    tty: true
                """);
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertThat(pod.getSpec().getContainers(), hasSize(2));
        Optional<Container> container = pod.getSpec().getContainers().stream()
                .filter(c -> "container".equals(c.getName()))
                .findFirst();
        assertTrue(container.isPresent());
        assertEquals(Long.valueOf(3000L), pod.getSpec().getSecurityContext().getRunAsUser());
        assertEquals(Long.valueOf(3000L), pod.getSpec().getSecurityContext().getRunAsGroup());
        assertEquals(Long.valueOf(2000L), container.get().getSecurityContext().getRunAsUser());
        assertEquals(Long.valueOf(2000L), container.get().getSecurityContext().getRunAsGroup());
    }

    @Test
    void yamlMergeVolumes() {
        PodTemplate parent = new PodTemplate();
        parent.setYaml(
                """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-label-value
                spec:
                  volumes:
                  - name: host-volume
                    hostPath:
                      path: /host/data
                """);

        PodTemplate child = new PodTemplate();
        child.setYaml(
                """
                spec:
                  volumes:
                  - name: host-volume2
                    hostPath:
                      path: /host/data2
                """);
        child.setInheritFrom("parent");
        child.setYamlMergeStrategy(merge());
        setupStubs();
        PodTemplate result = combine(parent, child);
        Pod pod = new PodTemplateBuilder(result, slave).build();
        assertEquals("some-label-value", pod.getMetadata().getLabels().get("some-label")); // inherit from parent
        assertThat(pod.getSpec().getVolumes(), hasSize(3));
        Optional<Volume> hostVolume = pod.getSpec().getVolumes().stream()
                .filter(v -> "host-volume".equals(v.getName()))
                .findFirst();
        assertTrue(hostVolume.isPresent());
        assertThat(hostVolume.get().getHostPath().getPath(), equalTo("/host/data")); // parent value
        Optional<Volume> hostVolume2 = pod.getSpec().getVolumes().stream()
                .filter(v -> "host-volume2".equals(v.getName()))
                .findFirst();
        assertTrue(hostVolume2.isPresent());
        assertThat(hostVolume2.get().getHostPath().getPath(), equalTo("/host/data2")); // child value
    }

    @ParameterizedTest(name = "directConnection={0}")
    @ValueSource(booleans = {true, false})
    void testOverridesContainerSpec(boolean directConnection) throws Exception {
        cloud.setDirectConnection(directConnection);
        PodTemplate template = new PodTemplate();
        ContainerTemplate cT = new ContainerTemplate("jnlp", "jenkinsci/jnlp-slave:latest");
        template.setContainers(List.of(cT));
        template.setYaml(loadYamlFile("pod-overrides.yaml"));
        setupStubs();
        Pod pod = new PodTemplateBuilder(template, slave).build();

        Map<String, Container> containers = toContainerMap(pod);
        assertEquals(1, containers.size());
        Container jnlp = containers.get("jnlp");
        assertEquals(1, jnlp.getVolumeMounts().size(), "Wrong number of volume mounts: " + jnlp.getVolumeMounts());
        validateContainers(pod, slave, directConnection);
    }

    @Test
    void whenRuntimeClassNameIsSetDoNotSetDefaultNodeSelector() {
        setupStubs();
        PodTemplate template = new PodTemplate();
        template.setYaml("spec:\n" + "  runtimeClassName: windows");
        Pod pod = new PodTemplateBuilder(template, slave).build();
        assertEquals("windows", pod.getSpec().getRuntimeClassName());
        assertThat(pod.getSpec().getNodeSelector(), anEmptyMap());
    }

    private Map<String, Container> toContainerMap(Pod pod) {
        return pod.getSpec().getContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
    }

    private Map<String, Container> toInitContainerMap(Pod pod) {
        return pod.getSpec().getInitContainers().stream()
                .collect(Collectors.toMap(Container::getName, Function.identity()));
    }

    private String loadYamlFile(String s) throws Exception {
        return new String(IOUtils.toByteArray(getClass().getResourceAsStream(s)));
    }

    private YamlMergeStrategy overrides() {
        return new Overrides();
    }

    private YamlMergeStrategy merge() {
        return new Merge();
    }
}
