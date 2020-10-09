package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Always;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.recipes.LocalData;
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
    public void getJenkinsUrlOrNull_NoJenkinsUrl() {
        JenkinsLocationConfiguration.get().setUrl(null);
        KubernetesCloud cloud = new KubernetesCloud("name");
        String url = cloud.getJenkinsUrlOrNull();
        assertNull(url);
    }

    @Test
    public void getJenkinsUrlOrNull_UrlInCloud() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocationinsysprop");
        KubernetesCloud cloud = new KubernetesCloud("name");
        cloud.setJenkinsUrl("http://mylocation");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrNull());
    }

    @Test
    public void getJenkinsUrlOrNull_UrlInSysprop() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrNull());
    }

    @Test
    public void getJenkinsUrlOrNull_UrlInLocation() {
        JenkinsLocationConfiguration.get().setUrl("http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrNull());
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
            public KubernetesClient connect() {
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
            public KubernetesClient connect()  {
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

        // an unset containerCap a.k.a. "concurrency limit" means unlimited, or 200 in this case
        Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(test, 200);
        assertEquals(200, plannedNodes.size());

        // set to "0" means disabled
        cloud.setContainerCapStr("0");
        podTemplate.setInstanceCap(20);
        plannedNodes = cloud.provision(test, 200);
        assertEquals(0, plannedNodes.size());

        // set to negative also means disabled
        cloud.setContainerCapStr("-42");
        podTemplate.setInstanceCap(20);
        plannedNodes = cloud.provision(test, 200);
        assertEquals(0, plannedNodes.size());

        // set to a positive number sets the concurrency limit
        cloud.setContainerCapStr("10");
        podTemplate.setInstanceCap(20);
        plannedNodes = cloud.provision(test, 200);
        assertEquals(10, plannedNodes.size());
    }

    @Test
    public void testPodLabels() {
        List<PodLabel> defaultPodLabelsList = PodLabel.fromMap(KubernetesCloud.DEFAULT_POD_LABELS);
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals(KubernetesCloud.DEFAULT_POD_LABELS, cloud.getPodLabelsMap());
        assertEquals(defaultPodLabelsList, cloud.getPodLabels());
        assertEquals(cloud.getPodLabelsMap(), cloud.getLabels());

        List<PodLabel> labels = PodLabel.listOf("foo", "bar", "cat", "dog");
        cloud.setPodLabels(labels);
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("foo", "bar");
        expected.put("cat", "dog");
        assertEquals(expected, cloud.getPodLabelsMap());
        assertEquals(cloud.getPodLabelsMap(), cloud.getLabels());
        assertEquals(new ArrayList<>(labels), cloud.getPodLabels());

        cloud.setPodLabels(null);
        assertEquals(KubernetesCloud.DEFAULT_POD_LABELS, cloud.getPodLabelsMap());
        assertEquals(defaultPodLabelsList, cloud.getPodLabels());

        cloud.setPodLabels(new ArrayList<>());
        assertEquals(KubernetesCloud.DEFAULT_POD_LABELS, cloud.getPodLabelsMap());
        assertEquals(cloud.getPodLabelsMap(), cloud.getLabels());
        assertEquals(defaultPodLabelsList, cloud.getPodLabels());
    }

    @Test
    public void testLabels() {
        KubernetesCloud cloud = new KubernetesCloud("name");

        List<PodLabel> labels = PodLabel.listOf("foo", "bar", "cat", "dog");
        cloud.setPodLabels(labels);
        Map<String, String> labelsMap = new LinkedHashMap<>();
        for (PodLabel l : labels) {
            labelsMap.put(l.getKey(), l.getValue());
        }
        cloud.setLabels(labelsMap);
        assertEquals(new LinkedHashMap<>(labelsMap), cloud.getPodLabelsMap());
        assertEquals(labels, cloud.getPodLabels());


        cloud.setLabels(null);
        assertEquals(ImmutableMap.of("jenkins", "slave"), cloud.getPodLabelsMap());
        assertEquals(ImmutableMap.of("jenkins", "slave"), cloud.getLabels());

        cloud.setLabels(new LinkedHashMap<>());
        assertEquals(ImmutableMap.of("jenkins", "slave"), cloud.getPodLabelsMap());
        assertEquals(ImmutableMap.of("jenkins", "slave"), cloud.getLabels());
    }

    @Test
    public void copyConstructor() throws Exception {
        PodTemplate pt = new PodTemplate();
        pt.setName("podTemplate");

        KubernetesCloud cloud = new KubernetesCloud("name");
        ArrayList<String> objectProperties = Lists.newArrayList("templates", "podRetention", "podLabels", "labels", "serverCertificate");
        for (String property: PropertyUtils.describe(cloud).keySet()) {
            if (PropertyUtils.isWriteable(cloud, property)) {
                Class<?> propertyType = PropertyUtils.getPropertyType(cloud, property);
                if (propertyType == String.class) {
                    if (property.endsWith("Str")) {
                        // setContainerCapStr
                        // setMaxRequestsPerHostStr
                        PropertyUtils.setProperty(cloud, property, RandomStringUtils.randomNumeric(3));
                    } else {
                        PropertyUtils.setProperty(cloud, property, RandomStringUtils.randomAlphabetic(10));
                    }
                } else if (propertyType == int.class) {
                    PropertyUtils.setProperty(cloud, property, RandomUtils.nextInt());
                } else if (propertyType == Integer.class) {
                    PropertyUtils.setProperty(cloud, property, Integer.valueOf(RandomUtils.nextInt()));
                } else if (propertyType == boolean.class) {
                    PropertyUtils.setProperty(cloud, property, RandomUtils.nextBoolean());
                } else if (!objectProperties.contains(property)) {
                    fail("Unhandled field in copy constructor: " + property);
                }
            }
        }
        cloud.setServerCertificate("-----BEGIN CERTIFICATE-----");
        cloud.setTemplates(Collections.singletonList(pt));
        cloud.setPodRetention(new Always());
        cloud.setPodLabels(PodLabel.listOf("foo", "bar", "cat", "dog"));
        cloud.setLabels(ImmutableMap.of("foo", "bar"));

        KubernetesCloud copy = new KubernetesCloud("copy", cloud);
        assertEquals("copy", copy.name);
        assertEquals("Expected cloud from copy constructor to be equal to the source except for name", cloud, copy);
    }

    @Test
    public void defaultWorkspaceVolume() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        j.jenkins.clouds.add(cloud);
        j.jenkins.save();
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("configureClouds/");
        HtmlForm f = p.getFormByName("config");
        HtmlButton buttonExtends = HtmlFormUtil.getButtonByCaption(f, "Pod Templates...");
        buttonExtends.click();
        HtmlButton buttonAdd = HtmlFormUtil.getButtonByCaption(f, "Add Pod Template");
        buttonAdd.click();
        HtmlButton buttonDetails = HtmlFormUtil.getButtonByCaption(f, "Pod Template details...");
        buttonDetails.click();
        DomElement templates = p.getElementByName("templates");
        HtmlInput templateName = getInputByName(templates, "_.name");
        templateName.setValueAttribute("default-workspace-volume");
        j.submit(f);
        cloud = j.jenkins.clouds.get(KubernetesCloud.class);
        PodTemplate podTemplate = cloud.getTemplates().get(0);
        assertEquals("default-workspace-volume", podTemplate.getName());
        assertEquals(WorkspaceVolume.getDefault(), podTemplate.getWorkspaceVolume());
    }

    @Test
    public void minRetentionTimeout() {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        assertEquals(KubernetesCloud.DEFAULT_RETENTION_TIMEOUT_MINUTES, cloud.getRetentionTimeout());
        cloud.setRetentionTimeout(0);
        assertEquals(KubernetesCloud.DEFAULT_RETENTION_TIMEOUT_MINUTES, cloud.getRetentionTimeout());
    }

    @Test
    @LocalData
    public void emptyKubernetesCloudReadResolve() {
        KubernetesCloud cloud = j.jenkins.clouds.get(KubernetesCloud.class);
        assertEquals(KubernetesCloud.DEFAULT_RETENTION_TIMEOUT_MINUTES, cloud.getRetentionTimeout());
        assertEquals(Integer.MAX_VALUE, cloud.getContainerCap());
        assertEquals(KubernetesCloud.DEFAULT_MAX_REQUESTS_PER_HOST, cloud.getMaxRequestsPerHost());
        assertEquals(PodRetention.getKubernetesCloudDefault(), cloud.getPodRetention());
        assertEquals(KubernetesCloud.DEFAULT_WAIT_FOR_POD_SEC, cloud.getWaitForPodSec());
    }

    public HtmlInput getInputByName(DomElement root, String name) {
        DomNodeList<HtmlElement> inputs = root.getElementsByTagName("input");
        for (HtmlElement input : inputs) {
            if (name.equals(input.getAttribute("name"))) {
                return (HtmlInput) input;
            }
        }
        return null;
    }

}
