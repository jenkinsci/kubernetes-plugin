package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

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
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PlannedNodeBuilder;
import org.csanchez.jenkins.plugins.kubernetes.PlannedNodeBuilderFactory;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verifies the optional tracked builder returns a cloud-stats {@link TrackedPlannedNode}, that a real
 * provisioning round records exactly one {@code PROVISIONING} activity, and that the extension seam is
 * respected (JENKINS-67256).
 */
@WithJenkins
class TrackedPlannedNodeBuilderTest {

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

        NodeProvisioner.PlannedNode plannedNode = new TrackedPlannedNodeBuilder()
                .cloud(cloud)
                .template(template)
                .numExecutors(1)
                .build();

        assertTrue(plannedNode instanceof TrackedPlannedNode, "the tracked builder should return a TrackedPlannedNode");
        ProvisioningActivity.Id id = ((TrackedPlannedNode) plannedNode).getId();
        assertNotNull(id);
        assertEquals("Cloud", id.getCloudName());
        assertEquals("Template", id.getTemplateName());
        assertRegex(id.getNodeName(), "^template-[0-9a-z]{5}$");
    }

    @Test
    void testProvisioningRecordsActivity() {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        template.setLabel("foo");
        cloud.addTemplate(template);
        r.jenkins.clouds.add(cloud);

        Label label = Label.get("foo");
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new Cloud.CloudState(label, 0), 1);
        assertFalse(planned.isEmpty(), "the cloud should plan a node for a matching label");
        NodeProvisioner.PlannedNode plannedNode = planned.iterator().next();
        assertTrue(plannedNode instanceof TrackedPlannedNode, "provisioning should go through the tracked builder");
        ProvisioningActivity.Id id = ((TrackedPlannedNode) plannedNode).getId();

        // cloud-stats opens the PROVISIONING activity from the tracked planned node when the attempt starts.
        for (CloudProvisioningListener cpl : CloudProvisioningListener.all()) {
            cpl.onStarted(cloud, label, planned);
        }

        CloudStatistics statistics = CloudStatistics.get();
        ProvisioningActivity activity = statistics.getActivityFor(id);
        assertNotNull(activity, "cloud-stats should record a provisioning activity for the attempt");
        assertEquals(id, activity.getId());
        assertEquals(ProvisioningActivity.Phase.PROVISIONING, activity.getCurrentPhase());

        long matching = statistics.getActivities().stream()
                .filter(a -> a.getId().getFingerprint() == id.getFingerprint())
                .count();
        assertEquals(1, matching, "exactly one activity should be recorded for the attempt");
    }

    @Test
    void testCreateInstanceReturnsTrackedByDefault() {
        // With cloud-stats present and no downstream factory, the tracked builder is the default despite
        // its negative ordinal.
        assertTrue(
                PlannedNodeBuilderFactory.createInstance() instanceof TrackedPlannedNodeBuilder,
                "with cloud-stats installed the default builder should be the tracked one");
    }

    @Test
    void testExtensionSeamUsesDownstreamFactory() {
        // Our tracked factory is registered at ordinal -1 precisely so a downstream factory (default
        // ordinal 0) still wins the seam.
        PlannedNodeBuilder builder = PlannedNodeBuilderFactory.createInstance();
        assertTrue(
                builder instanceof FixedPlannedNodeBuilder,
                "a registered downstream factory should be preferred over the tracked default builder");
    }

    @TestExtension("testExtensionSeamUsesDownstreamFactory")
    public static class FixedPlannedNodeBuilderFactory extends PlannedNodeBuilderFactory {
        @Override
        public PlannedNodeBuilder newInstance() {
            return new FixedPlannedNodeBuilder();
        }
    }

    /**
     * A stand-in downstream builder. build() delegates to the tracked builder so that, even if the
     * scoped {@link TestExtension} ever leaked into another test, provisioning would still work.
     */
    public static final class FixedPlannedNodeBuilder extends PlannedNodeBuilder {
        @Override
        public NodeProvisioner.PlannedNode build() {
            return new TrackedPlannedNodeBuilder()
                    .cloud(getCloud())
                    .template(getTemplate())
                    .label(getLabel())
                    .numExecutors(getNumExecutors())
                    .build();
        }
    }
}
