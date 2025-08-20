package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class KubernetesCloudTest {

    private JenkinsRule j;

    @SuppressWarnings("unused")
    private final LogRecorder logs = new LogRecorder()
            .record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()), Level.ALL);

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @AfterEach
    void afterEach() {
        System.getProperties().remove("KUBERNETES_JENKINS_URL");
    }

    @Test
    void configRoundTrip() throws Exception {
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
    void testInheritance() {
        ContainerTemplate jnlp = new ContainerTemplate("jnlp", "jnlp:1");
        ContainerTemplate maven = new ContainerTemplate("maven", "maven:1");
        maven.setTtyEnabled(true);
        maven.setCommand("cat");

        PodVolume podVolume = new EmptyDirVolume("/some/path", true);
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setLabel("parent");
        parent.setContainers(List.of(jnlp));
        parent.setVolumes(List.of(podVolume));

        ContainerTemplate maven2 = new ContainerTemplate("maven", "maven:2");
        PodTemplate withNewMavenVersion = new PodTemplate();
        withNewMavenVersion.setContainers(List.of(maven2));

        PodTemplate result = PodTemplateUtils.combine(parent, withNewMavenVersion);
    }

    @Test
    void getJenkinsUrlOrDie_NoJenkinsUrl() {
        JenkinsLocationConfiguration.get().setUrl(null);
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertThrows(IllegalStateException.class, () -> cloud.getJenkinsUrlOrDie());
    }

    @Test
    void getJenkinsUrlOrDie_UrlInCloud() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocationinsysprop");
        KubernetesCloud cloud = new KubernetesCloud("name");
        cloud.setJenkinsUrl("http://mylocation");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrDie());
    }

    @Test
    void getJenkinsUrlOrDie_UrlInSysprop() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrDie());
    }

    @Test
    void getJenkinsUrlOrDie_UrlInLocation() {
        JenkinsLocationConfiguration.get().setUrl("http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrDie());
    }

    @Test
    void getJenkinsUrlOrNull_NoJenkinsUrl() {
        JenkinsLocationConfiguration.get().setUrl(null);
        KubernetesCloud cloud = new KubernetesCloud("name");
        String url = cloud.getJenkinsUrlOrNull();
        assertNull(url);
    }

    @Test
    void getJenkinsUrlOrNull_UrlInCloud() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocationinsysprop");
        KubernetesCloud cloud = new KubernetesCloud("name");
        cloud.setJenkinsUrl("http://mylocation");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrNull());
    }

    @Test
    void getJenkinsUrlOrNull_UrlInSysprop() {
        System.setProperty("KUBERNETES_JENKINS_URL", "http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrNull());
    }

    @Test
    void getJenkinsUrlOrNull_UrlInLocation() {
        JenkinsLocationConfiguration.get().setUrl("http://mylocation");
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals("http://mylocation/", cloud.getJenkinsUrlOrNull());
    }

    @Test
    void testKubernetesCloudDefaults() {
        KubernetesCloud cloud = new KubernetesCloud("name");
        assertEquals(PodRetention.getKubernetesCloudDefault(), cloud.getPodRetention());
    }

    @Test
    void testPodLabels() {
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
    void testLabels() {
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
    void copyConstructor() throws Exception {
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
                    PropertyUtils.setProperty(cloud, property, RandomUtils.nextInt());
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
                EqualsBuilder.reflectionEquals(cloud, copy, true, KubernetesCloud.class, "name"),
                "Expected cloud from copy constructor to be equal to the source except for name");
    }

    @Test
    void defaultWorkspaceVolume() throws Exception {
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
        assertNotNull(row);
    }

    @Test
    void minRetentionTimeout() {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        assertEquals(KubernetesCloud.DEFAULT_RETENTION_TIMEOUT_MINUTES, cloud.getRetentionTimeout());
        cloud.setRetentionTimeout(0);
        assertEquals(KubernetesCloud.DEFAULT_RETENTION_TIMEOUT_MINUTES, cloud.getRetentionTimeout());
    }

    @Test
    @LocalData
    void emptyKubernetesCloudReadResolve() {
        KubernetesCloud cloud = j.jenkins.clouds.get(KubernetesCloud.class);
        assertEquals(KubernetesCloud.DEFAULT_RETENTION_TIMEOUT_MINUTES, cloud.getRetentionTimeout());
        assertEquals(Integer.MAX_VALUE, cloud.getContainerCap());
        assertEquals(KubernetesCloud.DEFAULT_MAX_REQUESTS_PER_HOST, cloud.getMaxRequestsPerHost());
        assertEquals(PodRetention.getKubernetesCloudDefault(), cloud.getPodRetention());
        assertEquals(KubernetesCloud.DEFAULT_WAIT_FOR_POD_SEC, cloud.getWaitForPodSec());
    }

    @Test
    @LocalData
    void readResolveContainerCapZero() {
        KubernetesCloud cloud = j.jenkins.clouds.get(KubernetesCloud.class);
        assertEquals(Integer.MAX_VALUE, cloud.getContainerCap());
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
    void authorization() {
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
            var expectedMessage = "user is missing the Overall/Administer permission";
            var kubernetesCloud = j.jenkins.clouds.get(KubernetesCloud.class);
            assertAccessDenied(() -> kubernetesCloud.addTemplate(new PodTemplate()), expectedMessage);
            assertAccessDenied(() -> kubernetesCloud.removeTemplate(pt1), expectedMessage);
            assertAccessDenied(() -> kubernetesCloud.replaceTemplate(pt1, pt2), expectedMessage);
        }
        try (var ignored = asUser("manager")) {
            j.jenkins.clouds.get(KubernetesCloud.class).addTemplate(pt1);
        }
    }

    private static void assertAccessDenied(Executable executable, String expectedMessage) {
        assertThat(
                assertThrows(AccessDeniedException3.class, executable).getMessage(), containsString(expectedMessage));
    }

    private static @NonNull ACLContext asUser(String admin) {
        return ACL.as2(User.get(admin, true, Map.of()).impersonate2());
    }
}
