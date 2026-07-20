/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.Map.entry;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assertRegex;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Always;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Default;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Evicted;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.OnFailure;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

/**
 * @author Carlos Sanchez
 */
@WithJenkins
class KubernetesSlaveTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @WithoutJenkins
    @Test
    void testGetSlaveName() {
        List<? extends PodVolume> volumes = Collections.emptyList();
        List<ContainerTemplate> containers = Collections.emptyList();

        assertRegex(KubernetesSlave.getSlaveName(new PodTemplate("image", volumes)), "^jenkins-agent-[0-9a-z]{5}$");
        assertRegex(
                KubernetesSlave.getSlaveName(new PodTemplate("", volumes, containers)), "^jenkins-agent-[0-9a-z]{5}$");
        assertRegex(
                KubernetesSlave.getSlaveName(new PodTemplate("a name", volumes, containers)), ("^a-name-[0-9a-z]{5}$"));
        assertRegex(
                KubernetesSlave.getSlaveName(new PodTemplate("an_other_name", volumes, containers)),
                ("^an-other-name-[0-9a-z]{5}$"));
        assertRegex(
                KubernetesSlave.getSlaveName(new PodTemplate("whatever...", volumes, containers)),
                ("jenkins-agent-[0-9a-z]{5}"));
    }

    @Test
    void testProvisioningActivityId() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("x");
        template.setName("Template");
        KubernetesSlave slave =
                new KubernetesSlave.Builder().cloud(cloud).podTemplate(template).build();

        ProvisioningActivity.Id id = slave.getId();
        assertNotNull(id);
        assertEquals("Cloud", id.getCloudName());
        assertEquals("Template", id.getTemplateName());
        assertEquals(slave.getNodeName(), id.getNodeName());
    }

    @Test
    void testComputerExposesSlaveId() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("x");
        template.setName("Template");
        KubernetesSlave slave =
                new KubernetesSlave.Builder().cloud(cloud).podTemplate(template).build();

        KubernetesComputer computer = Mockito.spy(new KubernetesComputer(slave));
        doReturn(slave).when(computer).getNode();

        assertNotNull(computer.getId());
        assertSame(slave.getId(), computer.getId());
    }

    @Test
    void testLegacyDeserializationMintsId() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("x");
        template.setName("Template");
        KubernetesSlave slave =
                new KubernetesSlave.Builder().cloud(cloud).podTemplate(template).build();

        // Simulate an agent serialized by a version predating cloud-stats tracking: no <id> element.
        String xml = Jenkins.XSTREAM2.toXML(slave);
        String legacyXml = xml.replaceAll("(?s)<id( [^>]*)?>.*?</id>", "");
        assertFalse(legacyXml.contains("<id>"), "test fixture should have stripped the id element");

        KubernetesSlave restored = (KubernetesSlave) Jenkins.XSTREAM2.fromXML(legacyXml);

        assertNotNull(restored.getId(), "readResolve should mint a fresh id for a legacy agent");
        assertEquals("Cloud", restored.getId().getCloudName());
        assertEquals(restored.getNodeName(), restored.getId().getNodeName());
        // The friendly template name is not persisted and the PodTemplate cannot be resolved safely
        // during deserialization, so a legacy-minted id deliberately falls back to the persisted
        // template id for its template-name component. Asserted so this stays intentional rather than
        // drifting silently from the constructor-minted id.
        assertEquals(restored.getTemplateId(), restored.getId().getTemplateName());
    }

    @Test
    void testGetPod() throws Exception {
        Map<String, GetPodTestCase> testCases = Map.ofEntries(
                entry("assigned", (KubernetesCloud cloud, KubernetesSlave slave, PodResource podResource) -> {
                    Pod p = new Pod();
                    slave.assignPod(p);
                    Optional<Pod> pod = slave.getPod();
                    assertTrue(pod.isPresent());
                    assertSame(p, pod.get());
                    verify(cloud, never()).getPodResource(anyString(), anyString());
                }),
                entry("unassigned", (KubernetesCloud cloud, KubernetesSlave slave, PodResource podResource) -> {
                    Pod p = new Pod();
                    doReturn(podResource).when(cloud).getPodResource(eq("bar"), startsWith("foo-"));
                    when(podResource.get()).thenReturn(p);

                    // test
                    Optional<Pod> pod = slave.getPod();

                    // verify
                    assertTrue(pod.isPresent());
                    assertSame(p, pod.get());
                    verify(cloud).getPodResource(eq("bar"), startsWith("foo-"));
                }),
                entry("not found", (KubernetesCloud cloud, KubernetesSlave slave, PodResource podResource) -> {
                    doReturn(podResource).when(cloud).getPodResource(eq("bar"), startsWith("foo-"));
                    when(podResource.get()).thenReturn(null);

                    // test
                    Optional<Pod> pod = slave.getPod();

                    // verify
                    assertTrue(pod.isEmpty());
                    verify(cloud).getPodResource(eq("bar"), startsWith("foo-"));
                }),
                entry("auth failure", (KubernetesCloud cloud, KubernetesSlave slave, PodResource podResource) -> {
                    doThrow(KubernetesAuthException.class).when(cloud).getPodResource(eq("bar"), startsWith("foo-"));

                    // test
                    Optional<Pod> pod = slave.getPod();

                    // verify
                    assertTrue(pod.isEmpty());
                    verify(cloud).getPodResource(eq("bar"), startsWith("foo-"));
                }),
                entry("connect error", (KubernetesCloud cloud, KubernetesSlave slave, PodResource podResource) -> {
                    doThrow(IOException.class).when(cloud).getPodResource(eq("bar"), startsWith("foo-"));

                    // test
                    Optional<Pod> pod = slave.getPod();

                    // verify
                    assertTrue(pod.isEmpty());
                    verify(cloud).getPodResource(eq("bar"), startsWith("foo-"));
                }),
                entry("cloud not found", (KubernetesCloud cloud, KubernetesSlave slave, PodResource podResource) -> {
                    r.jenkins.clouds.clear();

                    // test
                    Optional<Pod> pod = slave.getPod();

                    // verify
                    assertTrue(pod.isEmpty());
                    verify(cloud, never()).getPodResource(eq("bar"), startsWith("foo-"));
                }));

        for (Map.Entry<String, GetPodTestCase> testCase : testCases.entrySet()) {
            KubernetesCloud cloud = Mockito.spy(new KubernetesCloud("kube"));
            PodResource podResource = Mockito.mock(PodResource.class);
            PodTemplate podTemplate = new PodTemplate("foo", Collections.emptyList(), Collections.emptyList());
            KubernetesSlave slave = new KubernetesSlave.Builder()
                    .podTemplate(podTemplate)
                    .cloud(cloud)
                    .build();
            slave.setNamespace("bar");
            r.jenkins.clouds.clear();
            r.jenkins.clouds.add(cloud);
            testCase.getValue().test(cloud, slave, podResource);
        }
    }

    private interface GetPodTestCase {
        void test(KubernetesCloud cloud, KubernetesSlave slave, PodResource podResource) throws Exception;
    }

    @Test
    void testGetPodRetention() {
        assertDoesNotThrow(() -> {
            List<KubernetesSlaveTestCase<PodRetention>> cases = Arrays.asList(
                    createPodRetentionTestCase(new Never(), new Default(), new Default()),
                    createPodRetentionTestCase(new Never(), new Always(), new Always()),
                    createPodRetentionTestCase(new Never(), new OnFailure(), new OnFailure()),
                    createPodRetentionTestCase(new Never(), new Never(), new Never()),
                    createPodRetentionTestCase(new OnFailure(), new Default(), new Default()),
                    createPodRetentionTestCase(new OnFailure(), new Always(), new Always()),
                    createPodRetentionTestCase(new OnFailure(), new OnFailure(), new OnFailure()),
                    createPodRetentionTestCase(new OnFailure(), new Never(), new Never()),
                    createPodRetentionTestCase(new Always(), new Default(), new Default()),
                    createPodRetentionTestCase(new Always(), new Always(), new Always()),
                    createPodRetentionTestCase(new Always(), new OnFailure(), new OnFailure()),
                    createPodRetentionTestCase(new Always(), new Never(), new Never()),
                    createPodRetentionTestCase(new Evicted(), new Default(), new Default()),
                    createPodRetentionTestCase(new Evicted(), new Always(), new Always()),
                    createPodRetentionTestCase(new Evicted(), new OnFailure(), new OnFailure()),
                    createPodRetentionTestCase(new Evicted(), new Never(), new Never()));
            KubernetesCloud cloud = new KubernetesCloud("test");
            r.jenkins.clouds.add(cloud);
            for (KubernetesSlaveTestCase<PodRetention> testCase : cases) {
                cloud.setPodRetention(testCase.getCloudPodRetention());
                KubernetesSlave testSlave = testCase.buildSubject(cloud);
                assertEquals(testCase.getExpectedResult(), testSlave.getPodRetention(cloud));
            }
        });
    }

    private KubernetesSlaveTestCase<PodRetention> createPodRetentionTestCase(
            PodRetention cloudRetention, PodRetention templateRetention, PodRetention expectedResult) {
        return new KubernetesSlaveTestBuilder<PodRetention>()
                .withCloudPodRetention(cloudRetention)
                .withTemplatePodRetention(templateRetention)
                .withExpectedResult(expectedResult)
                .build();
    }

    public static class KubernetesSlaveTestCase<T> {

        private PodRetention cloudPodRetention;
        private PodTemplate podTemplate;
        private String podPhase;
        private T expectedResult;

        public KubernetesSlave buildSubject(KubernetesCloud cloud) throws Exception {
            return new KubernetesSlave.Builder()
                    .cloud(cloud)
                    .podTemplate(podTemplate)
                    .build();
        }

        public PodRetention getCloudPodRetention() {
            return this.cloudPodRetention;
        }

        public T getExpectedResult() {
            return this.expectedResult;
        }

        public PodTemplate getPodTemplate() {
            return this.podTemplate;
        }

        public String getPodPhase() {
            return this.podPhase;
        }
    }

    public static class KubernetesSlaveTestBuilder<T> {

        private PodRetention cloudPodRetention;
        private PodRetention templatePodRetention;
        private String podPhase;
        private T expectedResult;

        public KubernetesSlaveTestBuilder<T> withExpectedResult(T expectedResult) {
            this.expectedResult = expectedResult;
            return this;
        }

        public KubernetesSlaveTestBuilder<T> withCloudPodRetention(PodRetention podRetention) {
            this.cloudPodRetention = podRetention;
            return this;
        }

        public KubernetesSlaveTestBuilder<T> withTemplatePodRetention(PodRetention podRetention) {
            this.templatePodRetention = podRetention;
            return this;
        }

        public KubernetesSlaveTestBuilder<T> withPodPhase(String podPhase) {
            this.podPhase = podPhase;
            return this;
        }

        public KubernetesSlaveTestCase<T> build() {
            ContainerTemplate testContainer = new ContainerTemplate("busybox", "busybox");
            PodTemplate testTemplate = new PodTemplate();
            testTemplate.setPodRetention(templatePodRetention);
            testTemplate.setName("test-template");
            testTemplate.setLabel("test-template");
            testTemplate.setContainers(List.of(testContainer));

            KubernetesSlaveTestCase<T> testCase = new KubernetesSlaveTestCase<>();
            testCase.cloudPodRetention = cloudPodRetention;
            testCase.podTemplate = testTemplate;
            testCase.expectedResult = expectedResult;
            testCase.podPhase = podPhase;
            return testCase;
        }
    }
}
