package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assertRegex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class StandardPlannedNodeBuilderTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void testBuild() {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        r.jenkins.clouds.add(cloud);

        NodeProvisioner.PlannedNode plannedNode = new StandardPlannedNodeBuilder()
                .cloud(cloud)
                .template(template)
                .numExecutors(1)
                .build();

        assertTrue(plannedNode instanceof TrackedPlannedNode);
        ProvisioningActivity.Id id = ((TrackedPlannedNode) plannedNode).getId();
        assertNotNull(id);
        assertEquals("Cloud", id.getCloudName());
        assertEquals("Template", id.getTemplateName());
        assertRegex(id.getNodeName(), "^template-[0-9a-z]{5}$");
    }

    @Test
    void testProvisioningRecordsActivity() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        template.setLabel("foo");
        cloud.addTemplate(template);
        r.jenkins.clouds.add(cloud);

        Label label = Label.get("foo");
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new Cloud.CloudState(label, 0), 1);
        assertFalse(planned.isEmpty(), "cloud should plan a node for a matching label");
        NodeProvisioner.PlannedNode plannedNode = planned.iterator().next();
        assertTrue(plannedNode instanceof TrackedPlannedNode);
        ProvisioningActivity.Id id = ((TrackedPlannedNode) plannedNode).getId();

        // Mirror what NoDelayProvisionerStrategy and core's NodeProvisioner do after provisioning.
        for (CloudProvisioningListener cpl : CloudProvisioningListener.all()) {
            cpl.onStarted(cloud, label, planned);
        }

        CloudStatistics statistics = CloudStatistics.get();
        assertNotNull(statistics);
        ProvisioningActivity activity = statistics.getActivityFor(id);
        assertNotNull(activity, "cloud-stats should record a provisioning activity for the attempt");
        assertEquals(id, activity.getId());
        assertEquals(ProvisioningActivity.Phase.PROVISIONING, activity.getCurrentPhase());

        // Exactly one activity for this provisioning attempt: no duplicates, no orphans.
        long matching = statistics.getActivities().stream()
                .filter(a -> a.getId().getFingerprint() == id.getFingerprint())
                .count();
        assertEquals(1, matching, "expected exactly one activity for the attempt");
    }

    @Test
    void testExtensionSeamUsesDownstreamFactory() {
        // Adding cloud-stats tracking to the default builder must not break the PlannedNodeBuilder
        // extension point: a downstream plugin's factory still wins over StandardPlannedNodeBuilder.
        PlannedNodeBuilder builder = PlannedNodeBuilderFactory.createInstance();
        assertTrue(
                builder instanceof FixedPlannedNodeBuilder,
                "a registered downstream factory should be preferred over the default builder");
    }

    @TestExtension("testExtensionSeamUsesDownstreamFactory")
    public static class FixedPlannedNodeBuilderFactory extends PlannedNodeBuilderFactory {
        @Override
        public PlannedNodeBuilder newInstance() {
            return new FixedPlannedNodeBuilder();
        }
    }

    /**
     * A stand-in downstream builder. build() delegates to the standard builder so that, even if the
     * scoped {@link TestExtension} ever leaked into another test, provisioning would still work.
     */
    public static final class FixedPlannedNodeBuilder extends PlannedNodeBuilder {
        @Override
        public NodeProvisioner.PlannedNode build() {
            return new StandardPlannedNodeBuilder()
                    .cloud(getCloud())
                    .template(getTemplate())
                    .label(getLabel())
                    .numExecutors(getNumExecutors())
                    .build();
        }
    }
}
