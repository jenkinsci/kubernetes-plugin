package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.VolumeMount;
import jenkins.model.JenkinsLocationConfiguration;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.csanchez.jenkins.plugins.kubernetes.ContainerTemplateTestUtils.containerTemplate;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_JNLP_URL;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_LOCATION_URL;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_NAME;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_SECRET;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesCloudTest {

    private KubernetesCloud cloud = new KubernetesCloud("test", null, "http://localhost:8080", "default", null, "", 0,
            0, /* retentionTimeoutMinutes= */ 5);

    @Mock
    private JenkinsLocationConfiguration jenkinsLocationConfiguration;
    private String jenkinsUrl = "jenkinsUrl";
    private String jenkinsLocationConfigurationUrl = "locationConfigurationUrl";

    @Before
    public void setUp() {
        cloud = spy(cloud);
        doReturn(jenkinsLocationConfiguration).when(cloud).getJenkinsLocationConfiguration();
        doReturn(jenkinsLocationConfigurationUrl).when(jenkinsLocationConfiguration).getUrl();
        cloud.setJenkinsUrl(jenkinsUrl);
    }

    @Test
    public void testInheritance() {

        ContainerTemplate jnlp = new ContainerTemplate("jnlp", "jnlp:1");
        ContainerTemplate maven = new ContainerTemplate("maven", "maven:1");
        maven.setTtyEnabled(true);
        maven.setCommand("cat");

        PodVolume podVolume = new EmptyDirVolume("/some/path", true);
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setLabel("parent");
        parent.setContainers(asList(jnlp));
        parent.setVolumes(asList(podVolume));


        ContainerTemplate maven2 = new ContainerTemplate("maven", "maven:2");
        PodTemplate withNewMavenVersion = new PodTemplate();
        withNewMavenVersion.setContainers(asList(maven2));

        PodTemplateUtils.combine(parent, withNewMavenVersion);
    }

    @Test
    public void testParseDockerCommand() {
        assertNull(cloud.parseDockerCommand(""));
        assertNull(cloud.parseDockerCommand(null));
        assertEquals(ImmutableList.of("bash"), cloud.parseDockerCommand("bash"));
        assertEquals(ImmutableList.of("bash", "-c", "x y"), cloud.parseDockerCommand("bash -c \"x y\""));
        assertEquals(ImmutableList.of("a", "b", "c", "d"), cloud.parseDockerCommand("a b c d"));
    }

    @Test
    public void testIsAgentTemplate() {
        assertTrue(cloud.isJenkinsAgentTemplate(containerTemplate("jnlp", false, false)));
        assertFalse(cloud.isJenkinsAgentTemplate(containerTemplate("JNLP", false, false)));
        assertTrue(cloud.isJenkinsAgentTemplate(containerTemplate("self-starter", true, true)));
        assertTrue(cloud.isJenkinsAgentTemplate(containerTemplate("self-starter", true, false)));
    }

    @Test
    public void shouldPopulateEnvVarsForSimpleSlaveContainer() {
        final String nodeName = "nodeName";
        final String computerName = "computerName";
        final String computerUrl = "computerUrl";
        final String computerJnlpMac = "computerJnlpMac";

        SlaveInfo slaveInfo = new SlaveInfo(nodeName, computerName, computerUrl, computerJnlpMac);
        ArrayList<PodEnvVar> globalEnvVars = Lists.newArrayList();
        globalEnvVars.add(new PodEnvVar("globalEnvVar1", "globalEnvVar1Value"));

        Container container = cloud.createContainer(slaveInfo, containerTemplate("containerTemplate", false, false),
                globalEnvVars, Lists.newArrayList());
        List<EnvVar> envVars = container.getEnv();

        assertHavingEnvVar(envVars, "globalEnvVar1", "globalEnvVar1Value");
        assertHavingEnvVar(envVars, JENKINS_LOCATION_URL, jenkinsLocationConfigurationUrl);
        assertHavingEnvVar(envVars, JENKINS_URL, jenkinsUrl);
        assertHavingEnvVar(envVars, JENKINS_SECRET, computerJnlpMac);
        assertHavingEnvVar(envVars, JENKINS_NAME, computerName);
        assertHavingEnvVar(envVars, JENKINS_JNLP_URL, format("%s/%s%s", jenkinsUrl, computerUrl, "slave-agent.jnlp"));
    }

    @Test
    public void shouldPopulateEnvVarsForSelfRegisteringSlaveContainer() {
        final String nodeName = "nodeName";

        SlaveInfo slaveInfo = new SlaveInfo(nodeName);
        ArrayList<PodEnvVar> globalEnvVars = Lists.newArrayList();
        globalEnvVars.add(new PodEnvVar("globalEnvVar1", "globalEnvVar1Value"));

        Container container = cloud.createContainer(slaveInfo, containerTemplate("containerTemplate", false, false),
                globalEnvVars, Lists.newArrayList());
        List<EnvVar> envVars = container.getEnv();

        assertHavingEnvVar(envVars, "globalEnvVar1", "globalEnvVar1Value");
        assertHavingEnvVar(envVars, JENKINS_LOCATION_URL, jenkinsLocationConfigurationUrl);
        assertHavingEnvVar(envVars, JENKINS_URL, jenkinsUrl);
        assertNotHavingEnvVar(envVars, JENKINS_SECRET);
        assertNotHavingEnvVar(envVars, JENKINS_NAME);
        assertNotHavingEnvVar(envVars, JENKINS_JNLP_URL);
    }

    @Test
    public void shouldAddJnlpAgentIfMissingInContainerTemplate() {
        mockContainerCreation();
        PodTemplate podTemplate =
                podTemplateMock(Lists.newArrayList(), asList(containerTemplate("image1"), containerTemplate("image2")));
        SlaveInfo slaveInfo = new SlaveInfo("nodeName", "computerName", "computerUrl", "computerJnlpMac");

        Map<String, Container> containerMap = cloud.getNameToContainerMap(slaveInfo, podTemplate, Maps.newHashMap());

        // 2 + 1 JNLP slave to make it all available on Jenkins
        assertThat(containerMap).hasSize(3);
    }

    @Test
    public void shouldNotAddJnlpAgentIfContainerTemplateHasAgent() {
        mockContainerCreation();
        PodTemplate podTemplate =
                podTemplateMock(Lists.newArrayList(), asList(containerTemplate("agent", true), containerTemplate("image2")));
        SlaveInfo slaveInfo = new SlaveInfo("nodeName", "computerName", "computerUrl", "computerJnlpMac");

        Map<String, Container> containerMap = cloud.getNameToContainerMap(slaveInfo, podTemplate, Maps.newHashMap());

        assertThat(containerMap).hasSize(2);
    }

    @Test
    public void shouldFailIfContainerTemplateHasManyAgents() {
        mockContainerCreation();
        PodTemplate podTemplate =
                podTemplateMock(Lists.newArrayList(),
                        asList(containerTemplate("agent1", true), containerTemplate("agent2", true)));
        SlaveInfo slaveInfo = new SlaveInfo("nodeName", "computerName", "computerUrl", "computerJnlpMac");

        assertThatThrownBy(() -> cloud.getNameToContainerMap(slaveInfo, podTemplate, Maps.newHashMap()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Template contains at least 2 agent container images - should be only one");
    }

    @Test
    public void shouldProvidePlannedNodes() throws Exception {
        ArgumentCaptor<Callable> callableCaptor = ArgumentCaptor.forClass(Callable.class);
        stubGetPlannedNodesAndCaptureCallback(callableCaptor);

        Collection<NodeProvisioner.PlannedNode> plannedNodes =
                cloud.getPlannedNodes(mock(Label.class), 1,
                        asList(podTemplate(containerTemplate("mysql"), containerTemplate("jnlp"))));

        assertThat(plannedNodes).hasSize(1);
        assertThat(callableCaptor.getValue().getClass()).isEqualTo(KubernetesCloud.SimpleProvisioningCallback.class);
    }

    private void stubGetPlannedNodesAndCaptureCallback(ArgumentCaptor<Callable> callableCaptor) throws Exception {
        doReturn(true).when(cloud).addProvisionedSlave(any(PodTemplate.class), any(Label.class));
        doReturn(mock(NodeProvisioner.PlannedNode.class))
                .when(cloud).getPlannedNode(any(PodTemplate.class), callableCaptor.capture());
    }

    @Test
    public void shouldProvidePlannedNodesWithSelfRegisteredSlave() throws Exception {
        ArgumentCaptor<Callable> callableCaptor = ArgumentCaptor.forClass(Callable.class);
        stubGetPlannedNodesAndCaptureCallback(callableCaptor);

        Collection<NodeProvisioner.PlannedNode> plannedNodes =
                cloud.getPlannedNodes(mock(Label.class), 1,
                        asList(podTemplate(containerTemplate("swarm", true, true), containerTemplate("mysql"))));

        assertThat(plannedNodes).hasSize(1);
        assertThat(callableCaptor.getValue().getClass()).isEqualTo(KubernetesCloud.SelfRegisteringSlaveCallback.class);
    }

    private void mockContainerCreation() {
        doReturn(mock(Container.class))
                .when(cloud).createContainer(any(SlaveInfo.class), any(ContainerTemplate.class),
                anyCollectionOf(PodEnvVar.class), anyCollectionOf(VolumeMount.class));
    }

    private void assertHavingEnvVar(List<EnvVar> envVars, String name, String value) {
        assertThat(envVars).extracting("name", "value").contains(tuple(name, value));
    }

    private void assertNotHavingEnvVar(List<EnvVar> envVars, String name) {
        assertThat(envVars).extracting("name").doesNotContain(name);
    }

    private PodTemplate podTemplate(ContainerTemplate... containerTemplates) {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setContainers(asList(containerTemplates));
        return podTemplate;
    }

    private PodTemplate podTemplateMock(List<PodEnvVar> envVars, List<ContainerTemplate> containerTemplates) {
        PodTemplate podTemplate = mock(PodTemplate.class);
        doReturn(envVars).when(podTemplate).getEnvVars();
        doReturn(containerTemplates).when(podTemplate).getContainers();
        return podTemplate;
    }

    @Test
    public void testParseLivenessProbe() {
        assertNull(cloud.parseLivenessProbe(""));
        assertNull(cloud.parseLivenessProbe(null));
        assertEquals(ImmutableList.of("docker","info"), cloud.parseLivenessProbe("docker info"));
        assertEquals(ImmutableList.of("echo","I said: 'I am alive'"), cloud.parseLivenessProbe("echo \"I said: 'I am alive'\""));
        assertEquals(ImmutableList.of("docker","--version"), cloud.parseLivenessProbe("docker --version"));
        assertEquals(ImmutableList.of("curl","-k","--silent","--output=/dev/null","https://localhost:8080"), cloud.parseLivenessProbe("curl -k --silent --output=/dev/null \"https://localhost:8080\""));
    }

}
