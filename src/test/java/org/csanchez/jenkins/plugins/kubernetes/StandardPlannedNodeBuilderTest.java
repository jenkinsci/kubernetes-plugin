package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assertRegex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hudson.slaves.NodeProvisioner;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class StandardPlannedNodeBuilderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testBuild() {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        StandardPlannedNodeBuilder builder = new StandardPlannedNodeBuilder();
        builder.cloud(cloud);
        builder.template(template);
        builder.numExecutors(1);

        NodeProvisioner.PlannedNode plannedNode = builder.build();
        assertTrue(plannedNode instanceof TrackedPlannedNode);
        ProvisioningActivity.Id id = ((TrackedPlannedNode) plannedNode).getId();
        assertEquals(id.getCloudName(), "Cloud");
        assertEquals(id.getTemplateName(), "Template");
        assertRegex(id.getNodeName(), "template-\\w{5}");
    }
}
