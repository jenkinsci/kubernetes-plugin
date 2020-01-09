package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class PodTemplateJenkinsTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-60537")
    public void singleLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo");
        assertEquals("foo" , podTemplate.getLabelsMap().get("jenkins/label"));
    }

    @Test
    @Issue("JENKINS-60537")
    public void multiLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo bar");
        assertEquals("foo_bar", podTemplate.getLabelsMap().get("jenkins/label"));
    }
}
