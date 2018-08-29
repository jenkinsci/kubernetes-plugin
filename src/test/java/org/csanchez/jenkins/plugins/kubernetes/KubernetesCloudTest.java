package org.csanchez.jenkins.plugins.kubernetes;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;

import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import jenkins.model.JenkinsLocationConfiguration;

public class KubernetesCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @After
    public void tearDown() {
        System.getProperties().remove("KUBERNETES_JENKINS_URL");
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
        parent.setContainers(Arrays.asList(jnlp));
        parent.setVolumes(Arrays.asList(podVolume));

        ContainerTemplate maven2 = new ContainerTemplate("maven", "maven:2");
        PodTemplate withNewMavenVersion = new PodTemplate();
        withNewMavenVersion.setContainers(Arrays.asList(maven2));

        PodTemplate result = PodTemplateUtils.combine(parent, withNewMavenVersion);
    }

    @Test(expected = IllegalStateException.class)
    public void getJenkinsUrlOrDie_NoJenkinsUrl() {
        JenkinsLocationConfiguration.get().setUrl(null);
        KubernetesCloud cloud = new KubernetesCloud("name");
        String url = cloud.getJenkinsUrlOrDie();
        fail("Should have thrown IllegalStateException at this point but got " + url + " instead.");
    }

    @Test
    public void getJenkinsUrlOrDie_UrlInCloud() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocationinsysprop");
        KubernetesCloud cloud = new KubernetesCloud("name");
        cloud.setJenkinsUrl("http://mylocation");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrDie());
    }

    @Test
    public void getJenkinsUrlOrDie_UrlInSysprop() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrDie());
    }

    @Test
    public void getJenkinsUrlOrDie_UrlInLocation() {
        JenkinsLocationConfiguration.get().setUrl("http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrDie());
    }

    @Test
    public void testKubernetesCloudDefaults() {
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals(PodRetention.getKubernetesCloudDefault(), cloud.getPodRetention());
    }

    @Test
    public void testInstanceCap() {
        KubernetesCloud cloud = new KubernetesCloud("name");
        cloud.setContainerCapStr("100");

        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setName("test");
        podTemplate.setLabel("test");
        podTemplate.setInstanceCap(5);

        cloud.addTemplate(podTemplate);

        Label test = Label.get("test");
        assertTrue(cloud.canProvision(test));

        Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(test, 200);

        assertEquals(5, plannedNodes.size());
    }
}
