package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.mockito.Mockito;

import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import jenkins.model.JenkinsLocationConfiguration;

public class KubernetesCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule().record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()),
            Level.ALL);

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
        KubernetesCloud cloud = new KubernetesCloud("name") {
            @Override
            public KubernetesClient connect() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException, CertificateEncodingException {
                KubernetesClient mockClient =  Mockito.mock(KubernetesClient.class);
                Mockito.when(mockClient.getNamespace()).thenReturn("default");
                MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> operation = Mockito.mock(MixedOperation.class);
                Mockito.when(operation.inNamespace(Mockito.anyString())).thenReturn(operation);
                Mockito.when(operation.withLabels(Mockito.anyMap())).thenReturn(operation);
                PodList podList = Mockito.mock(PodList.class);
                Mockito.when(podList.getItems()).thenReturn(new ArrayList<>());
                Mockito.when(operation.list()).thenReturn(podList);
                Mockito.when(mockClient.pods()).thenReturn(operation);
                return mockClient;
            }
        };

        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setName("test");
        podTemplate.setLabel("test");

        cloud.addTemplate(podTemplate);

        Label test = Label.get("test");
        assertTrue(cloud.canProvision(test));

        Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(test, 200);
        assertEquals(200, plannedNodes.size());

        podTemplate.setInstanceCap(5);
        plannedNodes = cloud.provision(test, 200);
        assertEquals(5, plannedNodes.size());
    }

    @Test
    public void testContainerCap() {
        KubernetesCloud cloud = new KubernetesCloud("name") {
            @Override
            public KubernetesClient connect() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException, IOException, CertificateEncodingException {
                KubernetesClient mockClient =  Mockito.mock(KubernetesClient.class);
                Mockito.when(mockClient.getNamespace()).thenReturn("default");
                MixedOperation<Pod, PodList, DoneablePod, PodResource<Pod, DoneablePod>> operation = Mockito.mock(MixedOperation.class);
                Mockito.when(operation.inNamespace(Mockito.anyString())).thenReturn(operation);
                Mockito.when(operation.withLabels(Mockito.anyMap())).thenReturn(operation);
                PodList podList = Mockito.mock(PodList.class);
                Mockito.when(podList.getItems()).thenReturn(new ArrayList<>());
                Mockito.when(operation.list()).thenReturn(podList);
                Mockito.when(mockClient.pods()).thenReturn(operation);
                return mockClient;
            }
        };

        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setName("test");
        podTemplate.setLabel("test");

        cloud.addTemplate(podTemplate);

        Label test = Label.get("test");
        assertTrue(cloud.canProvision(test));

        Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(test, 200);
        assertEquals(200, plannedNodes.size());

        cloud.setContainerCapStr("10");
        podTemplate.setInstanceCap(20);
        plannedNodes = cloud.provision(test, 200);
        assertEquals(10, plannedNodes.size());
    }

    @Test
    public void testLabelsStr() {
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals(KubernetesCloud.DEFAULT_POD_LABELS, cloud.getLabels());
        assertEquals("jenkins=slave", cloud.getLabelsStr());

        cloud.setLabelsStr("foo=bar");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("foo", "bar");
        assertEquals(expected, cloud.getLabels());
        assertEquals("foo=bar", cloud.getLabelsStr());

        cloud.setLabelsStr("  app.kubernetes.io/managed-by=jenkins   app.kubernetes.io/instance=dev  ");
        expected = new LinkedHashMap<>();
        expected.put("app.kubernetes.io/managed-by", "jenkins");
        expected.put("app.kubernetes.io/instance", "dev");
        assertEquals(expected, cloud.getLabels());
        assertEquals("app.kubernetes.io/managed-by=jenkins app.kubernetes.io/instance=dev", cloud.getLabelsStr());

        cloud.setLabelsStr("  app.kubernetes.io/managed-by=   app.kubernetes.io/instance  ");
        expected = new LinkedHashMap<>();
        expected.put("app.kubernetes.io/managed-by", "");
        expected.put("app.kubernetes.io/instance", null);
        assertEquals(expected, cloud.getLabels());
        assertEquals("app.kubernetes.io/managed-by app.kubernetes.io/instance", cloud.getLabelsStr());
    }

}
