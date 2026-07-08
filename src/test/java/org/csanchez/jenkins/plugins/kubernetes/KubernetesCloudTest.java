package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Node;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Always;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.recipes.LocalData;

public class KubernetesCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule()
            .record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()), Level.ALL);

    @BeforeClass
    public static void enableManagePermission() {
        // TODO remove when baseline contains https://github.com/jenkinsci/jenkins/pull/23873
        Jenkins.MANAGE.setEnabled(true);
    }

    @After
    public void tearDown() {
        System.getProperties().remove("KUBERNETES_JENKINS_URL");
    }

    @Test
    public void configRoundTrip() throws Exception {
        var cloud = new KubernetesCloud("kubernetes");
        var podTemplate = new PodTemplate();
        podTemplate.setName("test-template");
        podTemplate.setLabel("test");
        cloud.addTemplate(podTemplate);
        var jenkins = j.jenkins;
        jenkins.clouds.add(cloud);
        jenkins.save();
        j.submit(j.createWebClient().goTo("cloud/kubernetes/configure").getFormByName("config"));
        assertEquals(cloud, jenkins.clouds.get(KubernetesCloud.class));
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
        assertEquals(Collections.singletonMap("jenkins", "slave"), cloud.getPodLabelsMap());
        assertEquals(Collections.singletonMap("jenkins", "slave"), cloud.getLabels());

        cloud.setLabels(new LinkedHashMap<>());
        assertEquals(Collections.singletonMap("jenkins", "slave"), cloud.getPodLabelsMap());
        assertEquals(Collections.singletonMap("jenkins", "slave"), cloud.getLabels());
    }

    @Test
    public void copyConstructor() throws Exception {
        PodTemplate pt = new PodTemplate();
        pt.setName("podTemplate");

        KubernetesCloud cloud = new KubernetesCloud("name");
        var objectProperties = Set.of(
                "templates", "podRetention", "podLabels", "labels", "serverCertificate", "garbageCollection", "traits");
        for (String property : PropertyUtils.describe(cloud).keySet()) {
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
        cloud.setLabels(Collections.singletonMap("foo", "bar"));

        KubernetesCloud copy = new KubernetesCloud("copy", cloud);
        assertEquals("copy", copy.name);
        assertTrue(
                "Expected cloud from copy constructor to be equal to the source except for name",
                EqualsBuilder.reflectionEquals(cloud, copy, true, KubernetesCloud.class, "name"));
    }

    @Test
    public void defaultWorkspaceVolume() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        j.jenkins.clouds.add(cloud);
        j.jenkins.save();
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage p = wc.goTo("cloud/kubernetes/new");
        HtmlForm f = p.getFormByName("config");
        HtmlInput templateName = getInputByName(f, "_.name");
        templateName.setValue("default-workspace-volume");
        j.submit(f);
        cloud = j.jenkins.clouds.get(KubernetesCloud.class);
        PodTemplate podTemplate = cloud.getTemplates().get(0);
        assertEquals("default-workspace-volume", podTemplate.getName());
        assertEquals(WorkspaceVolume.getDefault(), podTemplate.getWorkspaceVolume());
        // test whether we can edit a template
        p = wc.goTo("cloud/kubernetes/template/" + podTemplate.getId() + "/");
        f = p.getFormByName("config");
        templateName = getInputByName(f, "_.name");
        templateName.setValue("default-workspace");
        j.submit(f);
        podTemplate = cloud.getTemplates().get(0);
        assertEquals("default-workspace", podTemplate.getName());
        p = wc.goTo("cloud/kubernetes/templates");
        DomElement row = p.getElementById("template_" + podTemplate.getId());
        assertTrue(row != null);
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

    @Test
    @LocalData
    public void readResolveContainerCapZero() {
        KubernetesCloud cloud = j.jenkins.clouds.get(KubernetesCloud.class);
        assertEquals(cloud.getContainerCap(), Integer.MAX_VALUE);
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

    @Test
    public void authorization() throws Exception {
        var securityRealm = j.createDummySecurityRealm();
        j.jenkins.setSecurityRealm(securityRealm);
        var authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.ADMINISTER).everywhere().to("admin");
        authorizationStrategy.grant(Jenkins.MANAGE).everywhere().to("manager");
        authorizationStrategy.grant(Jenkins.READ).everywhere().to("user");
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);
        j.jenkins.clouds.add(new KubernetesCloud("kubernetes"));
        var pt1 = new PodTemplate("one");
        var pt2 = new PodTemplate("two");
        try (var ignored = asUser("admin")) {
            j.jenkins.clouds.get(KubernetesCloud.class).addTemplate(pt1);
        }
        try (var ignored = asUser("user")) {
            var expectedMessage = "user is missing the Overall/Manage permission";
            var kubernetesCloud = j.jenkins.clouds.get(KubernetesCloud.class);
            assertAccessDenied(() -> kubernetesCloud.addTemplate(new PodTemplate()), expectedMessage);
            assertAccessDenied(() -> kubernetesCloud.removeTemplate(pt1), expectedMessage);
            assertAccessDenied(() -> kubernetesCloud.replaceTemplate(pt1, pt2), expectedMessage);
        }
        try (var ignored = asUser("manager")) {
            j.jenkins.clouds.get(KubernetesCloud.class).addTemplate(pt1);
        }
    }

    private static void assertAccessDenied(ThrowingRunnable throwingRunnable, String expectedMessage) {
        assertThat(
                assertThrows(AccessDeniedException3.class, throwingRunnable).getMessage(),
                containsString(expectedMessage));
    }

    private static @NonNull ACLContext asUser(String admin) {
        return ACL.as2(User.get(admin, true, Map.of()).impersonate2());
    }

    @SuppressWarnings("unchecked")
    private Map<String, SharedIndexInformer<Pod>> getInformersMap(KubernetesCloud cloud) throws Exception {
        Field field = KubernetesCloud.class.getDeclaredField("informers");
        field.setAccessible(true);
        return (Map<String, SharedIndexInformer<Pod>>) field.get(cloud);
    }

    @Test
    public void unregisterPodInformer_closesAndRemoves() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        Map<String, SharedIndexInformer<Pod>> informers = getInformersMap(cloud);

        @SuppressWarnings("unchecked")
        SharedIndexInformer<Pod> informer = mock(SharedIndexInformer.class);
        informers.put("my-namespace", informer);

        cloud.unregisterPodInformer("my-namespace");

        verify(informer).close();
        assertTrue("Informer should be removed from the map", informers.isEmpty());
    }

    @Test
    public void unregisterPodInformer_noopOnUnknownNamespace() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        Map<String, SharedIndexInformer<Pod>> informers = getInformersMap(cloud);

        @SuppressWarnings("unchecked")
        SharedIndexInformer<Pod> informer = mock(SharedIndexInformer.class);
        informers.put("other-namespace", informer);

        cloud.unregisterPodInformer("unknown-namespace");

        verify(informer, never()).close();
        assertEquals("Existing informer should not be affected", 1, informers.size());
    }

    /**
     * When two pods share the same namespace, terminating the first must NOT
     * close the informer — the second pod still needs it.
     * Replicates the guard logic from {@code KubernetesSlave._terminate()}.
     */
    @Test
    public void informerKeptWhileOtherPodsShareNamespace() throws Exception {
        String cloudName = "test-cloud";
        String sharedNs = "shared-ns";

        KubernetesCloud cloud = new KubernetesCloud(cloudName);
        j.jenkins.clouds.add(cloud);

        Map<String, SharedIndexInformer<Pod>> informers = getInformersMap(cloud);
        @SuppressWarnings("unchecked")
        SharedIndexInformer<Pod> informer = mock(SharedIndexInformer.class);
        informers.put(sharedNs, informer);

        PodTemplate template = new PodTemplate();
        template.setName("tpl");
        KubernetesSlave slave1 = new KubernetesSlave(
                template, "slave1", cloudName, "", RetentionStrategy.NOOP);
        slave1.setNamespace(sharedNs);
        KubernetesSlave slave2 = new KubernetesSlave(
                template, "slave2", cloudName, "", RetentionStrategy.NOOP);
        slave2.setNamespace(sharedNs);

        j.jenkins.addNode(slave1);
        j.jenkins.addNode(slave2);

        // Simulate the guard from _terminate() for slave1: slave2 still exists
        boolean namespaceStillInUse = j.jenkins.getNodes().stream()
                .filter(KubernetesSlave.class::isInstance)
                .map(KubernetesSlave.class::cast)
                .filter(s -> s != slave1)
                .filter(s -> cloudName.equals(s.getCloudName()))
                .anyMatch(s -> sharedNs.equals(s.getNamespace()));

        assertTrue("Namespace should still be in use by slave2", namespaceStillInUse);
        if (!namespaceStillInUse) {
            cloud.unregisterPodInformer(sharedNs);
        }
        verify(informer, never()).close();
        assertEquals("Informer must remain in the map", 1, informers.size());
    }

    /**
     * When the last pod in a namespace terminates, the informer MUST be closed.
     * Replicates the guard logic from {@code KubernetesSlave._terminate()}.
     */
    @Test
    public void informerClosedWhenLastPodInNamespaceTerminates() throws Exception {
        String cloudName = "test-cloud";
        String sharedNs = "shared-ns";

        KubernetesCloud cloud = new KubernetesCloud(cloudName);
        j.jenkins.clouds.add(cloud);

        Map<String, SharedIndexInformer<Pod>> informers = getInformersMap(cloud);
        @SuppressWarnings("unchecked")
        SharedIndexInformer<Pod> informer = mock(SharedIndexInformer.class);
        informers.put(sharedNs, informer);

        PodTemplate template = new PodTemplate();
        template.setName("tpl");
        KubernetesSlave slave1 = new KubernetesSlave(
                template, "slave1", cloudName, "", RetentionStrategy.NOOP);
        slave1.setNamespace(sharedNs);
        KubernetesSlave slave2 = new KubernetesSlave(
                template, "slave2", cloudName, "", RetentionStrategy.NOOP);
        slave2.setNamespace(sharedNs);

        j.jenkins.addNode(slave1);
        j.jenkins.addNode(slave2);

        // slave1 terminated and removed from Jenkins
        j.jenkins.removeNode(slave1);

        // Now simulate the guard from _terminate() for slave2 (last pod)
        boolean namespaceStillInUse = j.jenkins.getNodes().stream()
                .filter(KubernetesSlave.class::isInstance)
                .map(KubernetesSlave.class::cast)
                .filter(s -> s != slave2)
                .filter(s -> cloudName.equals(s.getCloudName()))
                .anyMatch(s -> sharedNs.equals(s.getNamespace()));

        if (!namespaceStillInUse) {
            cloud.unregisterPodInformer(sharedNs);
        }

        verify(informer).close();
        assertTrue("Informer must be removed from the map", informers.isEmpty());
    }

    /**
     * Informers for a different cloud must not be affected when a pod terminates.
     */
    @Test
    public void informerNotAffectedByOtherCloud() throws Exception {
        String sharedNs = "shared-ns";

        KubernetesCloud cloudA = new KubernetesCloud("cloud-a");
        KubernetesCloud cloudB = new KubernetesCloud("cloud-b");
        j.jenkins.clouds.add(cloudA);
        j.jenkins.clouds.add(cloudB);

        Map<String, SharedIndexInformer<Pod>> informersA = getInformersMap(cloudA);
        @SuppressWarnings("unchecked")
        SharedIndexInformer<Pod> informerA = mock(SharedIndexInformer.class);
        informersA.put(sharedNs, informerA);

        Map<String, SharedIndexInformer<Pod>> informersB = getInformersMap(cloudB);
        @SuppressWarnings("unchecked")
        SharedIndexInformer<Pod> informerB = mock(SharedIndexInformer.class);
        informersB.put(sharedNs, informerB);

        PodTemplate template = new PodTemplate();
        template.setName("tpl");
        // One pod on cloud-a, one pod on cloud-b, same namespace
        KubernetesSlave slaveA = new KubernetesSlave(
                template, "slaveA", "cloud-a", "", RetentionStrategy.NOOP);
        slaveA.setNamespace(sharedNs);
        KubernetesSlave slaveB = new KubernetesSlave(
                template, "slaveB", "cloud-b", "", RetentionStrategy.NOOP);
        slaveB.setNamespace(sharedNs);

        j.jenkins.addNode(slaveA);
        j.jenkins.addNode(slaveB);

        // Simulate _terminate guard for slaveA (last pod on cloud-a)
        boolean namespaceStillInUse = j.jenkins.getNodes().stream()
                .filter(KubernetesSlave.class::isInstance)
                .map(KubernetesSlave.class::cast)
                .filter(s -> s != slaveA)
                .filter(s -> "cloud-a".equals(s.getCloudName()))
                .anyMatch(s -> sharedNs.equals(s.getNamespace()));

        if (!namespaceStillInUse) {
            cloudA.unregisterPodInformer(sharedNs);
        }

        // cloud-a informer closed (no other cloud-a pods in that namespace)
        verify(informerA).close();
        assertTrue("cloud-a informer must be removed", informersA.isEmpty());
        // cloud-b informer untouched
        verify(informerB, never()).close();
        assertEquals("cloud-b informer must remain", 1, informersB.size());
    }
}
