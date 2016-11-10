package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Preconditions;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Arrays;

import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import jenkins.model.Jenkins;

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
        parent.setImagePullSecrets(Arrays.asList(SECRET_1));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setImagePullSecrets(Arrays.asList(SECRET_1, SECRET_2, SECRET_3));

        PodTemplate template2 = new PodTemplate();
        template2.setName("template2");
        template2.setImagePullSecrets(Arrays.asList(SECRET_2, SECRET_3));

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
        parent.setImagePullSecrets(Arrays.asList(SECRET_1));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setInheritFrom("parent");
        template1.setServiceAccount("sa1");
        template1.setImagePullSecrets(Arrays.asList(SECRET_2, SECRET_3));


        PodTemplate result = unwrap(template1, Arrays.asList(parent, template1));
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
        parent.setImagePullSecrets(Arrays.asList(SECRET_1));
        parent.setContainers(Arrays.asList(JNLP_1, MAVEN_2));

        PodTemplate template1 = new PodTemplate();
        template1.setName("template1");
        template1.setLabel("template1");
        template1.setInheritFrom("parent");
        template1.setServiceAccount("sa1");
        template1.setImagePullSecrets(Arrays.asList(SECRET_2));
        template1.setContainers(Arrays.asList(JNLP_2));

        PodTemplate template2 = new PodTemplate();
        template2.setName("template2");
        template2.setLabel("template2");
        template2.setImagePullSecrets(Arrays.asList(SECRET_3));
        template2.setContainers(Arrays.asList(MAVEN_2));

        PodTemplate toUnwrap = new PodTemplate();
        toUnwrap.setName("toUnwrap");
        toUnwrap.setInheritFrom("template1 template2");


        PodTemplate result = unwrap(toUnwrap, Arrays.asList(parent, template1, template2));
        assertEquals(3, result.getImagePullSecrets().size());
        assertEquals("sa1", result.getServiceAccount());
        assertEquals("key:value", result.getNodeSelector());
        assertEquals(2, result.getContainers().size());

        ContainerTemplate mavenTemplate = result.getContainers().stream().filter(c -> c.getName().equals("maven")).findFirst().orElse(null);
        assertNotNull(mavenTemplate);
        assertEquals("maven:2", mavenTemplate.getImage());
    }
}
