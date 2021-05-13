/*
 * The MIT License
 *
 * Copyright (c) 2016, Carlos Sanchez and others
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

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import hudson.model.Node;
import hudson.tools.ToolLocationNodeProperty;
import io.fabric8.kubernetes.api.model.ConfigMapEnvSource;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent.SpecNested;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretEnvSource;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import org.jvnet.hudson.test.JenkinsRule;

public class PodTemplateUtilsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final PodImagePullSecret SECRET_1 = new PodImagePullSecret("secret1");
    private static final PodImagePullSecret SECRET_2 = new PodImagePullSecret("secret2");
    private static final PodImagePullSecret SECRET_3 = new PodImagePullSecret("secret3");

    private static final PodAnnotation ANNOTATION_1 = new PodAnnotation("key1", "value1");
    private static final PodAnnotation ANNOTATION_2 = new PodAnnotation("key2", "value2");
    private static final PodAnnotation ANNOTATION_3 = new PodAnnotation("key1", "value3");

    private static final ContainerTemplate JNLP_1 = new ContainerTemplate("jnlp", "jnlp:1");
    private static final ContainerTemplate JNLP_2 = new ContainerTemplate("jnlp", "jnlp:2");

    private static final ContainerTemplate MAVEN_1 = new ContainerTemplate("maven", "maven:1", "sh -c", "cat");
    private static final ContainerTemplate MAVEN_2 = new ContainerTemplate("maven", "maven:2");

    @Test
    public void shouldReturnContainerTemplateWhenParentIsNull() {
        ContainerTemplate result = combine(null, JNLP_2);
        assertEquals(result, JNLP_2);
    }

    @Test
    public void shouldOverrideTheImageAndInheritTheRest() {
        ContainerTemplate result = combine(MAVEN_1, MAVEN_2);
        assertEquals("maven:2", result.getImage());
        assertEquals("cat", result.getArgs());
    }

    @Test
    public void shouldReturnPodTemplateWhenParentIsNull() throws NoSuchAlgorithmException {
        PodTemplate template = new PodTemplate();
        template.setName("template");
        template.setServiceAccount("sa1");
        PodTemplate result = combine(null, template);
        assertEquals(result, template);
    }

    @Test
    public void shouldOverrideServiceAccountIfSpecified() throws NoSuchAlgorithmException {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setServiceAccount("sa");

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setServiceAccount("sa1");

        PodTemplate template2 = new PodTemplate();
        template1.setName("template2");

        PodTemplate result = combine(parent, template1);
        assertEquals("sa1", result.getServiceAccount());

        result = combine(parent, template2);
        assertEquals("sa", result.getServiceAccount());
    }

    @Test
    public void shouldOverrideNodeSelectorIfSpecified() throws NoSuchAlgorithmException {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setNodeSelector("key:value");

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setNodeSelector("key:value1");

        PodTemplate template2 = new PodTemplate();
        template2.setName("template2");

        PodTemplate result = combine(parent, template1);
        assertEquals("key:value1", result.getNodeSelector());

        result = combine(parent, template2);
        assertEquals("key:value", result.getNodeSelector());
    }

    @Test
    public void shouldCombineAllImagePullSecrets() throws NoSuchAlgorithmException {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setNodeSelector("key:value");
        parent.setImagePullSecrets(asList(SECRET_1));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setImagePullSecrets(asList(SECRET_1, SECRET_2, SECRET_3));

        PodTemplate template2 = new PodTemplate();
        template2.setName("template2");
        template2.setImagePullSecrets(asList(SECRET_2, SECRET_3));

        PodTemplate template3 = new PodTemplate();
        template3.setName("template3");

        PodTemplate result = combine(parent, template1);
        assertEquals(3, result.getImagePullSecrets().size());

        result = combine(parent, template2);
        assertEquals(3, result.getImagePullSecrets().size());

        result = combine(parent, template3);
        assertEquals(1, result.getImagePullSecrets().size());
    }

    @Test
    public void shouldCombineAllAnnotations() throws NoSuchAlgorithmException {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setNodeSelector("key:value");
        parent.setAnnotations(asList(ANNOTATION_1, ANNOTATION_2));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template");
        template1.setAnnotations(asList(ANNOTATION_3));

        PodTemplate result = combine(parent, template1);
        assertEquals(2, result.getAnnotations().size());
        assertEquals("value3", result.getAnnotations().get(0).getValue().toString());
    }

    @Test
    public void shouldCombineAllLabels() {
        Map<String, String> labelsMap1 = new HashMap<>();
        labelsMap1.put("label1", "pod1");
        labelsMap1.put("label2", "pod1");
        Pod pod1 = new PodBuilder().withNewMetadata().withLabels( //
                Collections.unmodifiableMap(labelsMap1) //
        ).endMetadata().withNewSpec().endSpec().build();

        Map<String, String> labelsMap2 = new HashMap<>();
        labelsMap2.put("label1", "pod2");
        labelsMap2.put("label3", "pod2");
        Pod pod2 = new PodBuilder().withNewMetadata().withLabels( //
                Collections.unmodifiableMap(labelsMap2) //
        ).endMetadata().withNewSpec().endSpec().build();

        Map<String, String> labels = combine(pod1, pod2).getMetadata().getLabels();
        assertThat(labels, hasEntry("label1", "pod2"));
        assertThat(labels, hasEntry("label2", "pod1"));
        assertThat(labels, hasEntry("label3", "pod2"));
    }

    @Test
    public void shouldUnwrapParent() throws NoSuchAlgorithmException {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setLabel("parent");
        parent.setServiceAccount("sa");
        parent.setNodeSelector("key:value");
        parent.setImagePullSecrets(asList(SECRET_1));
        parent.setYaml("Yaml");

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setInheritFrom("parent");
        template1.setServiceAccount("sa1");
        template1.setImagePullSecrets(asList(SECRET_2, SECRET_3));
        template1.setYaml("Yaml2");

        PodTemplate result = unwrap(template1, asList(parent, template1));
        assertEquals(3, result.getImagePullSecrets().size());
        assertEquals("sa1", result.getServiceAccount());
        assertEquals("key:value", result.getNodeSelector());
        assertThat(result.getYamls(), hasSize(2));
        assertThat(result.getYamls(), contains("Yaml", "Yaml2"));
    }

    @Test
    public void shouldDropNoDataWhenIdentical() throws NoSuchAlgorithmException {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setName("Name");
        podTemplate.setNamespace("NameSpace");
        podTemplate.setLabel("Label");
        podTemplate.setServiceAccount("ServiceAccount");
        podTemplate.setNodeSelector("NodeSelector");
        podTemplate.setNodeUsageMode(Node.Mode.EXCLUSIVE);
        podTemplate.setImagePullSecrets(asList(SECRET_1));
        podTemplate.setInheritFrom("Inherit");
        podTemplate.setInstanceCap(99);
        podTemplate.setSlaveConnectTimeout(99);
        podTemplate.setIdleMinutes(99);
        podTemplate.setActiveDeadlineSeconds(99);
        podTemplate.setServiceAccount("ServiceAccount");
        podTemplate.setYaml("Yaml");

        PodTemplate selfCombined = combine(podTemplate, podTemplate);

        assertEquals("Name", selfCombined.getName());
        assertEquals("NameSpace", selfCombined.getNamespace());
        assertEquals("Label", selfCombined.getLabel());
        assertEquals("ServiceAccount", selfCombined.getServiceAccount());
        assertEquals("NodeSelector", selfCombined.getNodeSelector());
        assertEquals(Node.Mode.EXCLUSIVE, selfCombined.getNodeUsageMode());
        assertEquals(asList(SECRET_1), selfCombined.getImagePullSecrets());
        assertEquals("Inherit", selfCombined.getInheritFrom());
        assertEquals(99, selfCombined.getInstanceCap());
        assertEquals(99, selfCombined.getSlaveConnectTimeout());
        assertEquals(99, selfCombined.getIdleMinutes());
        assertEquals(99, selfCombined.getActiveDeadlineSeconds());
        assertEquals("ServiceAccount", selfCombined.getServiceAccount());
        assertThat(selfCombined.getYamls(), hasItems("Yaml", "Yaml"));
    }

    @Test
    public void shouldUnwrapMultipleParents() throws NoSuchAlgorithmException {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setLabel("parent");
        parent.setServiceAccount("sa");
        parent.setNodeSelector("key:value");
        parent.setImagePullSecrets(asList(SECRET_1));
        parent.setContainers(asList(JNLP_1, MAVEN_2));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setLabel("template1");
        template1.setInheritFrom("parent");
        template1.setServiceAccount("sa1");
        template1.setImagePullSecrets(asList(SECRET_2));
        template1.setContainers(asList(JNLP_2));

        PodTemplate template2 = new PodTemplate();
        template2.setName("template2");
        template2.setLabel("template2");
        template2.setImagePullSecrets(asList(SECRET_3));
        template2.setContainers(asList(MAVEN_2));

        PodTemplate toUnwrap = new PodTemplate();
        toUnwrap.setName("toUnwrap");
        toUnwrap.setInheritFrom("template1 template2");

        PodTemplate result = unwrap(toUnwrap, asList(parent, template1, template2));
        assertEquals(3, result.getImagePullSecrets().size());
        assertEquals("sa1", result.getServiceAccount());
        assertEquals("key:value", result.getNodeSelector());
        assertEquals(2, result.getContainers().size());

        ContainerTemplate mavenTemplate = result.getContainers().stream().filter(c -> c.getName().equals("maven"))
                .findFirst().orElse(null);
        assertNotNull(mavenTemplate);
        assertEquals("maven:2", mavenTemplate.getImage());
    }

    @Test
    public void shouldCombineInitContainers() {
        Pod parentPod = new PodBuilder()
                .withNewMetadata().endMetadata()
                .withNewSpec()
                    .withInitContainers(new ContainerBuilder().withName("init-parent").build())
                .endSpec()
                .build();
        Pod childPod = new PodBuilder()
                .withNewMetadata().endMetadata()
                .withNewSpec()
                .withInitContainers(new ContainerBuilder().withName("init-child").build())
                .endSpec()
                .build();

        Pod combinedPod = combine(parentPod, childPod);
        List<Container> initContainers = combinedPod.getSpec().getInitContainers();
        assertThat(initContainers, hasSize(2));
        assertThat(initContainers.get(0).getName(), equalTo("init-parent"));
        assertThat(initContainers.get(1).getName(), equalTo("init-child"));
    }

    @Test
    public void childShouldOverrideParentInitContainer() {
        Pod parentPod = new PodBuilder()
                .withNewMetadata().endMetadata()
                .withNewSpec()
                .withInitContainers(new ContainerBuilder().withName("init").withNewImage("image-parent").build())
                .endSpec()
                .build();
        Pod childPod = new PodBuilder()
                .withNewMetadata().endMetadata()
                .withNewSpec()
                .withInitContainers(new ContainerBuilder().withName("init").withNewImage("image-child").build())
                .endSpec()
                .build();

        Pod combinedPod = combine(parentPod, childPod);
        List<Container> initContainers = combinedPod.getSpec().getInitContainers();
        assertThat(initContainers, hasSize(1));
        assertThat(initContainers.get(0).getName(), equalTo("init"));
        assertThat(initContainers.get(0).getImage(), equalTo("image-child"));
    }

    @Test
    public void shouldCombineAllPodKeyValueEnvVars() throws NoSuchAlgorithmException {
        PodTemplate template1 = new PodTemplate();
        KeyValueEnvVar podEnvVar1 = new KeyValueEnvVar("key-1", "value-1");
        template1.setEnvVars(singletonList(podEnvVar1));

        PodTemplate template2 = new PodTemplate();
        KeyValueEnvVar podEnvVar2 = new KeyValueEnvVar("key-2", "value-2");
        KeyValueEnvVar podEnvVar3 = new KeyValueEnvVar("key-3", "value-3");
        template2.setEnvVars(asList(podEnvVar2, podEnvVar3));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(podEnvVar1, podEnvVar2, podEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptyPodKeyValueEnvVars() throws NoSuchAlgorithmException {
        PodTemplate template1 = new PodTemplate();
        KeyValueEnvVar podEnvVar1 = new KeyValueEnvVar("", "value-1");
        template1.setEnvVars(singletonList(podEnvVar1));

        PodTemplate template2 = new PodTemplate();
        KeyValueEnvVar podEnvVar2 = new KeyValueEnvVar(null, "value-2");
        template2.setEnvVars(singletonList(podEnvVar2));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }

    @Test
    public void shouldCombineAllPodSecretEnvVars() throws NoSuchAlgorithmException {
        PodTemplate template1 = new PodTemplate();
        SecretEnvVar podSecretEnvVar1 = new SecretEnvVar("key-1", "secret-1", "secret-key-1", false);
        template1.setEnvVars(singletonList(podSecretEnvVar1));

        PodTemplate template2 = new PodTemplate();
        SecretEnvVar podSecretEnvVar2 = new SecretEnvVar("key-2", "secret-2", "secret-key-2", false);
        SecretEnvVar podSecretEnvVar3 = new SecretEnvVar("key-3", "secret-3", "secret-key-3", false);
        template2.setEnvVars(asList(podSecretEnvVar2, podSecretEnvVar3));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(podSecretEnvVar1, podSecretEnvVar2, podSecretEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptyPodSecretEnvVars() throws NoSuchAlgorithmException {
        PodTemplate template1 = new PodTemplate();
        SecretEnvVar podSecretEnvVar1 = new SecretEnvVar("", "secret-1", "secret-key-1", false);
        template1.setEnvVars(singletonList(podSecretEnvVar1));

        PodTemplate template2 = new PodTemplate();
        SecretEnvVar podSecretEnvVar2 = new SecretEnvVar(null, "secret-2", "secret-key-2", false);
        template2.setEnvVars(singletonList(podSecretEnvVar2));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }

    @Test
    public void shouldCombineAllEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        KeyValueEnvVar containerEnvVar1 = new KeyValueEnvVar("key-1", "value-1");
        template1.setEnvVars(singletonList(containerEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        KeyValueEnvVar containerEnvVar2 = new KeyValueEnvVar("key-2", "value-2");
        KeyValueEnvVar containerEnvVar3 = new KeyValueEnvVar("key-3", "value-3");
        template2.setEnvVars(asList(containerEnvVar2, containerEnvVar3));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(containerEnvVar1, containerEnvVar2, containerEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptyEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        KeyValueEnvVar containerEnvVar1 = new KeyValueEnvVar("", "value-1");
        template1.setEnvVars(singletonList(containerEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        KeyValueEnvVar containerEnvVar2 = new KeyValueEnvVar(null, "value-2");
        template2.setEnvVars(singletonList(containerEnvVar2));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }

    @Test
    public void shouldCombineAllSecretEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        SecretEnvVar containerSecretEnvVar1 = new SecretEnvVar("key-1", "secret-1", "secret-key-1", false);
        template1.setEnvVars(singletonList(containerSecretEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        SecretEnvVar containerSecretEnvVar2 = new SecretEnvVar("key-2", "secret-2", "secret-key-2", false);
        SecretEnvVar containerSecretEnvVar3 = new SecretEnvVar("key-3", "secret-3", "secret-key-3", false);
        template2.setEnvVars(asList(containerSecretEnvVar2, containerSecretEnvVar3));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(),
                contains(containerSecretEnvVar1, containerSecretEnvVar2, containerSecretEnvVar3));
    }

    @Test
    public void shouldCombineAllEnvFromSourcesWithoutChangingOrder() {
        EnvFromSource configMap1 = new EnvFromSource(new ConfigMapEnvSource("config-map-1", false), null, null);
        EnvFromSource secret1 = new EnvFromSource(null, null, new SecretEnvSource("secret-1", false));
        EnvFromSource configMap2 = new EnvFromSource(new ConfigMapEnvSource("config-map-2", true), null, null);
        EnvFromSource secret2 = new EnvFromSource(null, null, new SecretEnvSource("secret-2", true));

        Container container1 = new Container();
        container1.setEnvFrom(asList(configMap1, secret1));

        Container container2 = new Container();
        container2.setEnvFrom(asList(configMap2, secret2));

        Container result = combine(container1, container2);

        // Config maps and secrets could potentially overwrite each other's variables. We should preserve their order.
        assertThat(result.getEnvFrom(), contains(configMap1, secret1, configMap2, secret2));
        assertNull(result.getSecurityContext());
    }

    @Test
    public void shouldFilterOutEnvFromSourcesWithNullOrEmptyKey() {
        EnvFromSource noSource = new EnvFromSource(null, null, null);
        EnvFromSource noConfigMapKey = new EnvFromSource(new ConfigMapEnvSource(null, false), null, null);
        EnvFromSource emptyConfigMapKey = new EnvFromSource(new ConfigMapEnvSource("", false), null, null);
        EnvFromSource noSecretKey = new EnvFromSource(null, null, new SecretEnvSource(null, false));
        EnvFromSource emptySecretKey = new EnvFromSource(null, null, new SecretEnvSource("", false));

        Container container = new Container();
        container.setEnvFrom(asList(noSource, noConfigMapKey, emptyConfigMapKey, noSecretKey, emptySecretKey));

        Container result = combine(container, new Container());

        assertEquals(0, result.getEnvFrom().size());
    }

    @Test
    public void shouldCombineAllMounts() throws NoSuchAlgorithmException {
        PodTemplate template1 = new PodTemplate();
        HostPathVolume hostPathVolume1 = new HostPathVolume("/host/mnt1", "/container/mnt1");
        HostPathVolume hostPathVolume2 = new HostPathVolume("/host/mnt2", "/container/mnt2");
        template1.setVolumes(asList(hostPathVolume1, hostPathVolume2));

        PodTemplate template2 = new PodTemplate();
        HostPathVolume hostPathVolume3 = new HostPathVolume("/host/mnt3", "/container/mnt3");
        HostPathVolume hostPathVolume4 = new HostPathVolume("/host/mnt1", "/container/mnt4");
        template2.setVolumes(asList(hostPathVolume3, hostPathVolume4));

        PodTemplate result = combine(template1, template2);
        assertThat(result.getVolumes(), containsInAnyOrder(hostPathVolume1, hostPathVolume2, hostPathVolume3, hostPathVolume4));
    }

    private SpecNested<PodBuilder> podBuilder() {
        return new PodBuilder().withNewMetadata().endMetadata().withNewSpec();
    }

    private ContainerBuilder containerBuilder() {
        Map<String, Quantity> limitMap = new HashMap<>();
        limitMap.put("cpu", new Quantity());
        limitMap.put("memory", new Quantity());
        Map<String, Quantity> requestMap = new HashMap<>();
        limitMap.put("cpu", new Quantity());
        limitMap.put("memory", new Quantity());
        return new ContainerBuilder().withNewSecurityContext().endSecurityContext().withNewResources()
                .withLimits(Collections.unmodifiableMap(limitMap))
                .withRequests(Collections.unmodifiableMap(requestMap)).endResources();
    }

    @Test
    public void shouldCombineAllPodMounts() {
        VolumeMount vm1 = new VolumeMountBuilder().withMountPath("/host/mnt1").withName("volume-1").withReadOnly(false)
                .build();
        VolumeMount vm2 = new VolumeMountBuilder().withMountPath("/host/mnt2").withName("volume-2").withReadOnly(false)
                .build();
        VolumeMount vm3 = new VolumeMountBuilder().withMountPath("/host/mnt3").withName("volume-3").withReadOnly(false)
                .build();
        VolumeMount vm4 = new VolumeMountBuilder().withMountPath("/host/mnt1").withName("volume-4").withReadOnly(false)
                .build();
        Container container1 = containerBuilder().withName("jnlp").withVolumeMounts(vm1, vm2).build();
        Pod pod1 = podBuilder().withContainers(container1).endSpec().build();
        Container container2 = containerBuilder().withName("jnlp").withVolumeMounts(vm3, vm4).build();
        Pod pod2 = podBuilder().withContainers(container2).endSpec().build();

        Pod result = combine(pod1, pod2);
        List<Container> containers = result.getSpec().getContainers();
        assertEquals(1, containers.size());
        assertEquals(3, containers.get(0).getVolumeMounts().size());
        assertThat(containers.get(0).getVolumeMounts(), containsInAnyOrder(vm2, vm3, vm4));
    }

    @Test
    public void shouldCombineAllTolerations() {
        PodSpec podSpec1 = new PodSpec();
        Pod pod1 = new Pod();
        Toleration toleration1 = new Toleration("effect1", "key1", "oper1", Long.parseLong("1"), "val1");
        Toleration toleration2 = new Toleration("effect2", "key2", "oper2", Long.parseLong("2"), "val2");
        podSpec1.setTolerations(asList(toleration1, toleration2));
        pod1.setSpec(podSpec1);
        pod1.setMetadata(new ObjectMeta());

        PodSpec podSpec2 = new PodSpec();
        Pod pod2 = new Pod();
        Toleration toleration3 = new Toleration("effect3", "key3", "oper3", Long.parseLong("3"), "val3");
        Toleration toleration4 = new Toleration("effect4", "key4", "oper4", Long.parseLong("4"), "val4");
        podSpec2.setTolerations(asList(toleration3, toleration4));
        pod2.setSpec(podSpec2);
        pod2.setMetadata(new ObjectMeta());

        Pod result = combine(pod1, pod2);
        assertThat(result.getSpec().getTolerations(), containsInAnyOrder(toleration1, toleration2, toleration3, toleration4));
    }

    @Test
    public void shouldCombineAllPorts() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        PortMapping port1 = new PortMapping("port-1", 1000, 1000);
        template1.setPorts(Arrays.asList(port1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");

        assertThat(combine(template1, template2).getPorts(), contains(port1));

        PortMapping port2 = new PortMapping("port-2", 2000, 2000);
        template2.setPorts(Arrays.asList(port2));
        assertThat(combine(template1, template2).getPorts(), containsInAnyOrder(port1, port2));

        port2.setName("port-1");
        assertThat(combine(template1, template2).getPorts(), contains(port2));
    }

    @Test
    public void shouldCombineAllResources() {
        Container container1 = new Container();
        container1.setResources(new ResourceRequirementsBuilder() //
                .addToLimits("cpu", new Quantity("1")) //
                .addToLimits("memory", new Quantity("1Gi")) //
                .addToRequests("cpu", new Quantity("100m")) //
                .addToRequests("memory", new Quantity("156Mi")) //
                .build());

        Container container2 = new Container();
        container2.setResources(new ResourceRequirementsBuilder() //
                .addToLimits("cpu", new Quantity("2")) //
                .addToLimits("memory", new Quantity("2Gi")) //
                .addToRequests("cpu", new Quantity("200m")) //
                .addToRequests("memory", new Quantity("256Mi")) //
                .build());

        Container result = combine(container1, container2);

        assertQuantity("2", result.getResources().getLimits().get("cpu"));
        assertQuantity("2Gi", result.getResources().getLimits().get("memory"));
        assertQuantity("200m", result.getResources().getRequests().get("cpu"));
        assertQuantity("256Mi", result.getResources().getRequests().get("memory"));
    }

    /**
     * Use instead of {@link org.junit.Assert#assertEquals(Object, Object)} on {@link Quantity}.
     * @see <a href="https://github.com/fabric8io/kubernetes-client/issues/2034">kubernetes-client #2034</a>
     */
    public static void assertQuantity(String expected, Quantity actual) {
        if (Quantity.getAmountInBytes(new Quantity(expected)).compareTo(Quantity.getAmountInBytes(actual)) != 0) {
            fail("expected: " + expected + " but was: " + actual.getAmount() + actual.getFormat());
        }
    }

    @Test
    public void shouldFilterOutNullOrEmptySecretEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        SecretEnvVar containerSecretEnvVar1 = new SecretEnvVar("", "secret-1", "secret-key-1", false);
        template1.setEnvVars(singletonList(containerSecretEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        SecretEnvVar containerSecretEnvVar2 = new SecretEnvVar(null, "secret-2", "secret-key-2", false);
        template2.setEnvVars(singletonList(containerSecretEnvVar2));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }

    // Substitute tests

    @Test
    public void shouldIgnoreMissingProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        assertEquals("${key2}", substitute("${key2}", properties));
    }

    @Test
    public void shouldSubstituteSingleEnvVar() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        assertEquals("value1", substitute("${key1}", properties));
    }

    @Test
    public void shouldSubstituteMultipleEnvVars() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");
        assertEquals("value1 or value2", substitute("${key1} or ${key2}", properties));
    }

    @Test
    public void shouldSubstituteMultipleEnvVarsAndIgnoreMissing() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");
        assertEquals("value1 or value2 or ${key3}", substitute("${key1} or ${key2} or ${key3}", properties));
    }

    @Test
    public void shouldSubstituteMultipleEnvVarsAndNotUseDefaultsForMissing() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");
        assertEquals("value1 or value2 or ${key3}",
                substitute("${key1} or ${key2} or ${key3}", properties, "defaultValue"));
    }

    @Test
    public void testValidateLabelA() {
        assertTrue(validateLabel("1"));
        assertTrue(validateLabel("a"));
    }

    @Test
    public void testValidateLabelAb() {
        assertTrue(validateLabel("12"));
        assertTrue(validateLabel("ab"));
    }

    @Test
    public void testValidateLabelAbc() {
        assertTrue(validateLabel("123"));
        assertTrue(validateLabel("abc"));
    }

    @Test
    public void testValidateLabelAbcd() {
        assertTrue(validateLabel("1234"));
        assertTrue(validateLabel("abcd"));
    }

    @Test
    public void testValidateLabelMypod() {
        assertTrue(validateLabel("mypod"));
    }

    @Test
    public void testValidateLabelMyPodNested() {
        assertTrue(validateLabel("mypodNested"));
    }

    @Test
    public void testValidateLabelSpecialChars() {
        assertTrue(validateLabel("x-_.z"));
        assertFalse(validateLabel("one two"));
    }

    @Test
    public void testValidateLabelStartWithSpecialChars() {
        assertFalse(validateLabel("-x"));
    }

    @Test
    public void testValidateLabelLong() {
        assertTrue(validateLabel("123456789012345678901234567890123456789012345678901234567890123"));
        assertTrue(validateLabel("abcdefghijklmnopqrstuwxyzabcdefghijklmnopqrstuwxyzabcdefghijklm"));
    }

    @Test
    public void testValidateLabelTooLong() {
        assertFalse(validateLabel("1234567890123456789012345678901234567890123456789012345678901234"));
        assertFalse(validateLabel("abcdefghijklmnopqrstuwxyzabcdefghijklmnopqrstuwxyzabcdefghijklmn"));
    }

    @Test
    public void shouldCombineAllToolLocations() throws NoSuchAlgorithmException {

        PodTemplate podTemplate1 = new PodTemplate();
        List<ToolLocationNodeProperty> nodeProperties1 = new ArrayList<>();
        ToolLocationNodeProperty toolHome1 = new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation("toolKey1@Test", "toolHome1"));
        nodeProperties1.add(toolHome1);
        podTemplate1.setNodeProperties(nodeProperties1);

        PodTemplate podTemplate2 = new PodTemplate();
        List<ToolLocationNodeProperty> nodeProperties2 = new ArrayList<>();
        ToolLocationNodeProperty toolHome2 = new ToolLocationNodeProperty(new ToolLocationNodeProperty.ToolLocation("toolKey2@Test", "toolHome2"));
        nodeProperties2.add(toolHome2);
        podTemplate2.setNodeProperties(nodeProperties2);

        PodTemplate result = combine(podTemplate1,podTemplate2);

        assertThat(podTemplate1.getNodeProperties(), contains(toolHome1));
        assertThat(podTemplate2.getNodeProperties(), contains(toolHome2));
        assertThat(result.getNodeProperties(), contains(toolHome1, toolHome2));

    }

    @Test
    @Issue("JENKINS-57116")
    public void testParseYaml() {
        PodTemplateUtils.parseFromYaml("{}");
        PodTemplateUtils.parseFromYaml(null);
        PodTemplateUtils.parseFromYaml("");
    }
}
