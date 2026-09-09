package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

/**
 * Verifies cloud-stats provisioning is recorded even when a downstream {@code PlannedNodeBuilderFactory}
 * replaces the default builder with one that returns an untracked {@link NodeProvisioner.PlannedNode}
 * (i.e. not a cloud-stats {@code TrackedItem}). That path is what the cluster-gated
 * {@code KubernetesCloudStatsLifecycleTest} cannot exercise, so it is reproduced here against the public
 * cloud-stats seams with no cluster. The compensation is keyed and deduped by node name (JENKINS-67256).
 */
@WithJenkins
class KubernetesProvisioningActivityListenerTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    /** Registers a cloud named "Cloud" carrying one template that matches label "foo", and returns it. */
    private KubernetesCloud registerCloud() {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        template.setLabel("foo");
        cloud.addTemplate(template);
        r.jenkins.clouds.add(cloud);
        return cloud;
    }

    /**
     * Provisions one agent from a matching template and returns the (already-resolved) agent. Unlike a
     * real round, cloud-stats' {@code onStarted} is deliberately <em>not</em> fired, so no activity yet
     * exists — mirroring a downstream builder whose untracked planned node cloud-stats ignored.
     */
    private KubernetesSlave provisionAgent() throws Exception {
        Collection<NodeProvisioner.PlannedNode> planned =
                registerCloud().provision(new Cloud.CloudState(Label.get("foo"), 0), 1);
        return (KubernetesSlave) planned.iterator().next().future.get();
    }

    /** A plain planned node, as a downstream builder (e.g. CloudBees) would return: not a TrackedItem. */
    private static NodeProvisioner.PlannedNode untrackedPlannedNode(KubernetesSlave agent) {
        return new NodeProvisioner.PlannedNode(agent.getNodeName(), CompletableFuture.<Node>completedFuture(agent), 1);
    }

    private static long activityCountForNode(String nodeName) {
        return CloudStatistics.get().getActivities().stream()
                .filter(a -> nodeName.equals(a.getId().getNodeName()))
                .count();
    }

    private static ProvisioningActivity activityForNode(String nodeName) {
        return CloudStatistics.get().getActivities().stream()
                .filter(a -> nodeName.equals(a.getId().getNodeName()))
                .findFirst()
                .orElse(null);
    }

    private static KubernetesProvisioningActivityListener listener() {
        return ExtensionList.lookupSingleton(KubernetesProvisioningActivityListener.class);
    }

    @Test
    void testOpensProvisioningActivityForUntrackedPlannedNode() throws Exception {
        KubernetesSlave agent = provisionAgent();
        String nodeName = agent.getNodeName();
        assertEquals(0, activityCountForNode(nodeName), "precondition: no activity until the listener opens one");

        listener().onComplete(untrackedPlannedNode(agent), agent);

        ProvisioningActivity activity = activityForNode(nodeName);
        assertNotNull(activity, "the listener should open a PROVISIONING activity for an untracked planned node");
        assertEquals(nodeName, activity.getId().getNodeName());
        assertEquals("Cloud", activity.getId().getCloudName());
        assertEquals("Template", activity.getId().getTemplateName());
        assertEquals(
                ProvisioningActivity.Phase.PROVISIONING,
                activity.getCurrentPhase(),
                "the opened activity should start in PROVISIONING so cloud-stats can advance it");
        assertEquals(1, activityCountForNode(nodeName), "exactly one activity for the attempt");
    }

    @Test
    void testIsIdempotentForUntrackedPlannedNode() throws Exception {
        KubernetesSlave agent = provisionAgent();
        String nodeName = agent.getNodeName();

        // Each completion mints a fresh-fingerprint Id for the same node name; the guard must dedupe by
        // node name so the second call does not open a duplicate.
        listener().onComplete(untrackedPlannedNode(agent), agent);
        listener().onComplete(untrackedPlannedNode(agent), agent);

        assertEquals(1, activityCountForNode(nodeName), "a second completion must not open a duplicate activity");
    }

    @Test
    void testDoesNothingWhenPlannedNodeAlreadyTracked() throws Exception {
        KubernetesCloud cloud = registerCloud();
        Label label = Label.get("foo");
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new Cloud.CloudState(label, 0), 1);
        NodeProvisioner.PlannedNode trackedPlannedNode = planned.iterator().next();
        KubernetesSlave agent = (KubernetesSlave) trackedPlannedNode.future.get();
        String nodeName = agent.getNodeName();

        // Stock path: the planned node is a TrackedPlannedNode and cloud-stats opens the activity itself.
        // The listener must not open a second one.
        assertEquals(TrackedPlannedNode.class, trackedPlannedNode.getClass());
        for (CloudProvisioningListener cpl : CloudProvisioningListener.all()) {
            cpl.onStarted(cloud, label, planned);
        }
        assertEquals(1, activityCountForNode(nodeName), "precondition: cloud-stats opened exactly one activity");

        listener().onComplete(trackedPlannedNode, agent);

        assertEquals(
                1,
                activityCountForNode(nodeName),
                "a tracked planned node must be left to cloud-stats, with no duplicate");
    }

    @Test
    void testIgnoresNonKubernetesNode() {
        // Registered globally, so it must be a safe no-op for other clouds' agents.
        Node other = Mockito.mock(Node.class);
        NodeProvisioner.PlannedNode plannedNode =
                new NodeProvisioner.PlannedNode("other", CompletableFuture.completedFuture(other), 1);
        assertDoesNotThrow(() -> listener().onComplete(plannedNode, other));
        assertEquals(
                0, CloudStatistics.get().getActivities().size(), "no activity should be opened for a non-k8s node");
    }
}
