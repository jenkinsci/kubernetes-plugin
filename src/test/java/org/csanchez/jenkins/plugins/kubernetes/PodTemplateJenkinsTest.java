package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Map;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplate.LABEL_DIGEST_FUNCTION;
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
        assertEquals(LABEL_DIGEST_FUNCTION.hashString("foo").toString(), labelsMap.get("jenkins/label-digest"));
    }

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    public void multiLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo bar");
        Map<String, String> labelsMap = podTemplate.getLabelsMap();
        assertEquals("foo_bar", labelsMap.get("jenkins/label"));
        assertEquals(LABEL_DIGEST_FUNCTION.hashString("foo bar").toString(), labelsMap.get("jenkins/label-digest"));
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
}
