package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Preconditions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import jenkins.model.Jenkins;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.*;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.*;
import static org.mockito.Matchers.anyString;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Jenkins.class})
public class PodTemplateUtilsTest {

    private static final PodImagePullSecret SECRET_1 = new PodImagePullSecret("secret1");
    private static final PodImagePullSecret SECRET_2 = new PodImagePullSecret("secret2");
    private static final PodImagePullSecret SECRET_3 = new PodImagePullSecret("secret3");

    private static final ContainerTemplate JNLP_1 = new ContainerTemplate("jnlp", "jnlp:1");
    private static final ContainerTemplate JNLP_2 = new ContainerTemplate("jnlp", "jnlp:2");

    private static final ContainerTemplate MAVEN_1 = new ContainerTemplate("maven", "maven:1", "sh -c", "cat");
    private static final ContainerTemplate MAVEN_2 = new ContainerTemplate("maven", "maven:2");
    private static final ContainerTemplate GOLANG_1 = new ContainerTemplate("golang", "golang:1");

    private static final PodTemplate TEMPLATE_1 = new PodTemplate();

    @Mock
    private Jenkins jenkins;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Jenkins.class);
        PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
        Answer<Label> labeler = invocation -> {
            Preconditions.checkArgument(invocation != null && invocation.getArguments().length == 1 && invocation.getArguments()[0] instanceof String);
            return new LabelAtom((String)invocation.getArguments()[0]);
        };

        PowerMockito.doAnswer(labeler).when(jenkins).getLabel(anyString());
        PowerMockito.doAnswer(labeler).when(jenkins).getLabelAtom(anyString());
    }

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
    public void shouldCombineAllPodSimpleEnvVars() {
        PodTemplate template1 = new PodTemplate();
        PodEnvVar podEnvVar1 = new PodEnvVar("key-1", "value-1");
        template1.setEnvVars(singletonList(podEnvVar1));

        PodTemplate template2 = new PodTemplate();
        PodEnvVar podEnvVar2 = new PodEnvVar("key-2", "value-2");
        PodEnvVar podEnvVar3 = new PodEnvVar("key-3", "value-3");
        template2.setEnvVars(asList(podEnvVar2, podEnvVar3));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(podEnvVar1, podEnvVar2, podEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptyPodSimpleEnvVars() {
        PodTemplate template1 = new PodTemplate();
        PodEnvVar podEnvVar1 = new PodEnvVar("", "value-1");
        template1.setEnvVars(singletonList(podEnvVar1));

        PodTemplate template2 = new PodTemplate();
        PodEnvVar podEnvVar2 = new PodEnvVar(null, "value-2");
        template2.setEnvVars(singletonList(podEnvVar2));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }

    @Test
    public void shouldCombineAllPodSecretEnvVars() {
        PodTemplate template1 = new PodTemplate();
        PodSecretEnvVar podSecretEnvVar1 = new PodSecretEnvVar("key-1", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(podSecretEnvVar1));

        PodTemplate template2 = new PodTemplate();
        PodSecretEnvVar podSecretEnvVar2 = new PodSecretEnvVar("key-2", "secret-2", "secret-key-2");
        PodSecretEnvVar podSecretEnvVar3 = new PodSecretEnvVar("key-3", "secret-3", "secret-key-3");
        template2.setEnvVars(asList(podSecretEnvVar2, podSecretEnvVar3));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(podSecretEnvVar1, podSecretEnvVar2, podSecretEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptyPodSecretEnvVars() {
        PodTemplate template1 = new PodTemplate();
        PodSecretEnvVar podSecretEnvVar1 = new PodSecretEnvVar("", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(podSecretEnvVar1));

        PodTemplate template2 = new PodTemplate();
        PodSecretEnvVar podSecretEnvVar2 = new PodSecretEnvVar(null, "secret-2", "secret-key-2");
        template2.setEnvVars(singletonList(podSecretEnvVar2));

        PodTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }

    @Test
    public void shouldCombineAllSimpleEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        ContainerEnvVar containerEnvVar1 = new ContainerEnvVar("key-1", "value-1");
        template1.setEnvVars(singletonList(containerEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        ContainerEnvVar containerEnvVar2 = new ContainerEnvVar("key-2", "value-2");
        ContainerEnvVar containerEnvVar3 = new ContainerEnvVar("key-3", "value-3");
        template2.setEnvVars(asList(containerEnvVar2, containerEnvVar3));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(containerEnvVar1, containerEnvVar2, containerEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptySimpleEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        ContainerEnvVar containerEnvVar1 = new ContainerEnvVar("", "value-1");
        template1.setEnvVars(singletonList(containerEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        ContainerEnvVar containerEnvVar2 = new ContainerEnvVar(null, "value-2");
        template2.setEnvVars(singletonList(containerEnvVar2));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }

    @Test
    public void shouldCombineAllSecretEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        ContainerSecretEnvVar containerSecretEnvVar1 = new ContainerSecretEnvVar("key-1", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(containerSecretEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        ContainerSecretEnvVar containerSecretEnvVar2 = new ContainerSecretEnvVar("key-2", "secret-2", "secret-key-2");
        ContainerSecretEnvVar containerSecretEnvVar3 = new ContainerSecretEnvVar("key-3", "secret-3", "secret-key-3");
        template2.setEnvVars(asList(containerSecretEnvVar2, containerSecretEnvVar3));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), contains(containerSecretEnvVar1, containerSecretEnvVar2, containerSecretEnvVar3));
    }

    @Test
    public void shouldFilterOutNullOrEmptySecretEnvVars() {
        ContainerTemplate template1 = new ContainerTemplate("name-1", "image-1");
        ContainerSecretEnvVar containerSecretEnvVar1 = new ContainerSecretEnvVar("", "secret-1", "secret-key-1");
        template1.setEnvVars(singletonList(containerSecretEnvVar1));

        ContainerTemplate template2 = new ContainerTemplate("name-2", "image-2");
        ContainerSecretEnvVar containerSecretEnvVar2 = new ContainerSecretEnvVar(null, "secret-2", "secret-key-2");
        template2.setEnvVars(singletonList(containerSecretEnvVar2));

        ContainerTemplate result = combine(template1, template2);

        assertThat(result.getEnvVars(), empty());
    }
}