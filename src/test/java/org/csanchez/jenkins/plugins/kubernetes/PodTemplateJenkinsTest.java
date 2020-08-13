package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.hash.Hashing;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class PodTemplateJenkinsTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    public void singleLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo");
        assertEquals("foo" , podTemplate.getLabelsMap().get("jenkins/label"));
        assertEquals(Hashing.md5().hashString("foo").toString(), podTemplate.getLabelsMap().get("jenkins/label-md5"));
    }

    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    public void multiLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("foo bar");
        assertEquals("foo_bar", podTemplate.getLabelsMap().get("jenkins/label"));
        assertEquals(Hashing.md5().hashString("foo bar").toString(), podTemplate.getLabelsMap().get("jenkins/label-md5"));
    }
    
    @Test
    @Issue({"JENKINS-59690", "JENKINS-60537"})
    public void defaultLabel() {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(null);
        assertEquals("slave-default", podTemplate.getLabelsMap().get("jenkins/label"));
        assertEquals("0", podTemplate.getLabelsMap().get("jenkins/label-md5"));
    }
}
