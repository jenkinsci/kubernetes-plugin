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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.junit.Test;

public class PodTemplateUtilsTest {

    private static final PodImagePullSecret SECRET_1 = new PodImagePullSecret("secret1");
    private static final PodImagePullSecret SECRET_2 = new PodImagePullSecret("secret2");
    private static final PodImagePullSecret SECRET_3 = new PodImagePullSecret("secret3");

    private static final PodAnnotation ANNOTATION_1 = new PodAnnotation("key1", "value1");
    private static final PodAnnotation ANNOTATION_2 = new PodAnnotation("key2", "value2");

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
    public void shouldReturnPodTemplateWhenParentIsNull() {
        PodTemplate template = new PodTemplate();
        template.setName("template");
        template.setServiceAccount("sa1");
        PodTemplate result = combine(null, template);
        assertEquals(result, template);
    }


    @Test
    public void shouldOverrideServiceAccountIfSpecified() {
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
    public void shouldOverrideNodeSelectorIfSpecified() {
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
    public void shouldCombineAllImagePullSecrets() {
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
    public void shouldCombineAllAnnotations() {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setNodeSelector("key:value");
        parent.setAnnotations(asList(ANNOTATION_1));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template");
        template1.setAnnotations(asList(ANNOTATION_2));
        
        PodTemplate result = combine(parent, template1);
        assertEquals(2, result.getAnnotations().size());
    }


    @Test
    public void shouldUnwrapParent() {
        PodTemplate parent = new PodTemplate();
        parent.setName("parent");
        parent.setLabel("parent");
        parent.setServiceAccount("sa");
        parent.setNodeSelector("key:value");
        parent.setImagePullSecrets(asList(SECRET_1));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setInheritFrom("parent");
        template1.setServiceAccount("sa1");
        template1.setImagePullSecrets(asList(SECRET_2, SECRET_3));


        PodTemplate result = unwrap(template1, asList(parent, template1));
        assertEquals(3, result.getImagePullSecrets().size());
        assertEquals("sa1", result.getServiceAccount());
        assertEquals("key:value", result.getNodeSelector());
    }

    @Test
    public void shouldUnwrapMultipleParents() {
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

        ContainerTemplate mavenTemplate = result.getContainers().stream().filter(c -> c.getName().equals("maven")).findFirst().orElse(null);
        assertNotNull(mavenTemplate);
        assertEquals("maven:2", mavenTemplate.getImage());
    }

    @Test
    public void shouldCombineAllPodKeyValueEnvVars() {
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
    public void shouldFilterOutNullOrEmptyPodKeyValueEnvVars() {
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
    public void shouldCombineAllPodSecretEnvVars() {
        PodTemplate template1 = new PodTemplate();
        SecretEnvVar podSecretEnvVar1 = new SecretEnvVar("key-1", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(podSecretEnvVar1));

        PodTemplate template2 = new PodTemplate();
        SecretEnvVar podSecretEnvVar2 = new SecretEnvVar("key-2", "secret-2", "secret-key-2");
        SecretEnvVar podSecretEnvVar3 = new SecretEnvVar("key-3", "secret-3", "secret-key-3");
        template2.setEnvVars(asList(podSecretEnvVar2, podSecretEnvVar3));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(podSecretEnvVar1, podSecretEnvVar2, podSecretEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptyPodSecretEnvVars() {
        PodTemplate template1 = new PodTemplate();
        SecretEnvVar podSecretEnvVar1 = new SecretEnvVar("", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(podSecretEnvVar1));

        PodTemplate template2 = new PodTemplate();
        SecretEnvVar podSecretEnvVar2 = new SecretEnvVar(null, "secret-2", "secret-key-2");
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
        SecretEnvVar containerSecretEnvVar1 = new SecretEnvVar("key-1", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(containerSecretEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        SecretEnvVar containerSecretEnvVar2 = new SecretEnvVar("key-2", "secret-2", "secret-key-2");
        SecretEnvVar containerSecretEnvVar3 = new SecretEnvVar("key-3", "secret-3", "secret-key-3");
        template2.setEnvVars(asList(containerSecretEnvVar2, containerSecretEnvVar3));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(containerSecretEnvVar1, containerSecretEnvVar2, containerSecretEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptySecretEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        SecretEnvVar containerSecretEnvVar1 = new SecretEnvVar("", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(containerSecretEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        SecretEnvVar containerSecretEnvVar2 = new SecretEnvVar(null, "secret-2", "secret-key-2");
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
        assertEquals("value1 or value2 or ${key3}", substitute("${key1} or ${key2} or ${key3}", properties, "defaultValue"));
    }
}
