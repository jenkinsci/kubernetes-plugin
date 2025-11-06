package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplate.getLabelDigestFunction;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.Label;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PodTemplateJenkinsTest {

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    void singleLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo");
        Map<String, String> labelsMap = podTemplate.getLabelsMap();
        assertEquals("foo", labelsMap.get("jenkins/label"));
        MessageDigest labelDigestFunction = getLabelDigestFunction();
        labelDigestFunction.update("foo".getBytes(StandardCharsets.UTF_8));
        assertEquals(
                String.format("%040x", new BigInteger(1, labelDigestFunction.digest())),
                labelsMap.get("jenkins/label-digest"));
    }

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    void multiLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo bar");
        Map<String, String> labelsMap = podTemplate.getLabelsMap();
        assertEquals("foo_bar", labelsMap.get("jenkins/label"));
        MessageDigest labelDigestFunction = getLabelDigestFunction();
        labelDigestFunction.update("foo bar".getBytes(StandardCharsets.UTF_8));
        assertEquals(
                String.format("%040x", new BigInteger(1, labelDigestFunction.digest())),
                labelsMap.get("jenkins/label-digest"));
    }

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    void defaultLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(null);
        Map<String, String> labelsMap = podTemplate.getLabelsMap();
        assertEquals("slave-default", labelsMap.get("jenkins/label"));
        assertEquals("0", labelsMap.get("jenkins/label-digest"));
    }

    @Test
    void jenkinsLabels() {
        KubernetesCloud kubernetesCloud = new KubernetesCloud("kubernetes");
        j.jenkins.clouds.add(kubernetesCloud);
        PodTemplate podTemplate = new PodTemplate();
        kubernetesCloud.addTemplate(podTemplate);
        podTemplate.setLabel("foo bar");

        Set<String> labels = j.jenkins.getLabels().stream().map(Label::getName).collect(Collectors.toSet());
        assertThat(
                labels,
                Matchers.anyOf(
                        containsInAnyOrder("master", "foo", "bar"), containsInAnyOrder("built-in", "foo", "bar")));
    }
}
