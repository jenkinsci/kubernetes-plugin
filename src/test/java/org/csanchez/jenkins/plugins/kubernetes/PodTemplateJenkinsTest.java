package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Label;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplate.LABEL_DIGEST_FUNCTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;

public class PodTemplateJenkinsTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    public void singleLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo");
        Map<String, String> labelsMap = podTemplate.getLabelsMap();
        assertEquals("foo" , labelsMap.get("jenkins/label"));
        LABEL_DIGEST_FUNCTION.update("foo".getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.toString(LABEL_DIGEST_FUNCTION.digest()), labelsMap.get("jenkins/label-digest"));
    }

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    public void multiLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo bar");
        Map<String, String> labelsMap = podTemplate.getLabelsMap();
        assertEquals("foo_bar", labelsMap.get("jenkins/label"));
        LABEL_DIGEST_FUNCTION.update("foo bar".getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.toString(LABEL_DIGEST_FUNCTION.digest()), labelsMap.get("jenkins/label-digest"));
    }
    
    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    public void defaultLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(null);
        Map<String, String> labelsMap = podTemplate.getLabelsMap();
        assertEquals("slave-default", labelsMap.get("jenkins/label"));
        assertEquals("0", labelsMap.get("jenkins/label-digest"));
    }

    @Test
    public void jenkinsLabels() {
        KubernetesCloud kubernetesCloud = new KubernetesCloud("kubernetes");
        j.jenkins.clouds.add(kubernetesCloud);
        PodTemplate podTemplate = new PodTemplate();
        kubernetesCloud.addTemplate(podTemplate);
        podTemplate.setLabel("foo bar");
        assertThat(j.jenkins.getLabels().stream()
                .map(Label::getName)
                .collect(Collectors.toSet()),
                containsInAnyOrder("master", "foo", "bar")
        );
    }
}
