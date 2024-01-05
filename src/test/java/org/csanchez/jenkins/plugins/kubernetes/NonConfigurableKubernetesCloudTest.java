package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertEquals;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class NonConfigurableKubernetesCloudTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule()
            .record(
                    Logger.getLogger(NonConfigurableKubernetesCloudTest.class
                            .getPackage()
                            .getName()),
                    Level.ALL);

    @Test
    public void configRoundTrip() throws Exception {
        // create a cloud with a template
        var cloud = new KubernetesCloud("kubernetes");
        var podTemplate = new PodTemplate();
        podTemplate.setName("test-template");
        podTemplate.setLabel("test");
        cloud.addTemplate(podTemplate);
        var jenkins = j.jenkins;
        jenkins.clouds.add(cloud);
        jenkins.save();
        var readOnly = new NonConfigurableKubernetesCloud("NonConfigurableKubernetes", cloud);
        readOnly.removeTemplate(podTemplate); // should not remove anything
        var podTemplate2 = new PodTemplate();
        podTemplate2.setName("test-template-read-only");
        podTemplate2.setLabel("test-read-only");
        readOnly.addTemplate(podTemplate2);
        jenkins.clouds.add(readOnly);
        jenkins.save();
        assertEquals(2, jenkins.clouds.size()); // sanity check
        assertEquals(1, readOnly.getTemplates().size()); // remove should not have removed anything, nor should have add
        readOnly.getTemplates().forEach(t -> {
            if (t.getName().equals("test-template-read-only")) {
                throw new RuntimeException("Template should not be added");
            }
        });
    }
}
