package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputerFactory;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Verifies the tracked computer resolves its cloud-stats identity by node name from the recorded
 * activities, rather than storing it on the agent, and returns {@code null} for an untracked agent
 * (JENKINS-67256).
 */
@WithJenkins
class TrackedKubernetesComputerTest {

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

    @Test
    void testGetIdResolvesByNodeName() throws Exception {
        KubernetesCloud cloud = registerCloud();
        Label label = Label.get("foo");
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new Cloud.CloudState(label, 0), 1);
        NodeProvisioner.PlannedNode plannedNode = planned.iterator().next();
        ProvisioningActivity.Id minted = ((TrackedPlannedNode) plannedNode).getId();
        // cloud-stats opens the PROVISIONING activity when the provisioning attempt starts.
        for (CloudProvisioningListener cpl : CloudProvisioningListener.all()) {
            cpl.onStarted(cloud, label, planned);
        }
        KubernetesSlave agent = (KubernetesSlave) plannedNode.future.get();

        ProvisioningActivity.Id resolved = new TrackedKubernetesComputer(agent).getId();

        assertNotNull(resolved, "getId should resolve the activity minted for this agent's node name");
        assertEquals(agent.getNodeName(), resolved.getNodeName());
        assertEquals(minted, resolved, "the resolved Id must be the one the activity was recorded under");
        assertNotNull(
                CloudStatistics.get().getActivityFor(resolved), "the resolved Id must look up the recorded activity");
    }

    @Test
    void testGetIdIsNullForUntrackedAgent() throws Exception {
        // Provision an agent but never fire cloud-stats' onStarted, so no activity is recorded — the
        // legacy / untracked case. getId must be null so cloud-stats quietly does nothing.
        Collection<NodeProvisioner.PlannedNode> planned =
                registerCloud().provision(new Cloud.CloudState(Label.get("foo"), 0), 1);
        KubernetesSlave agent =
                (KubernetesSlave) planned.iterator().next().future.get();

        assertNull(new TrackedKubernetesComputer(agent).getId());
    }

    @Test
    void testFactoryYieldsTrackedComputer() throws Exception {
        Collection<NodeProvisioner.PlannedNode> planned =
                registerCloud().provision(new Cloud.CloudState(Label.get("foo"), 0), 1);
        KubernetesSlave agent =
                (KubernetesSlave) planned.iterator().next().future.get();

        KubernetesComputer computer = KubernetesComputerFactory.createInstance(agent);

        assertEquals(TrackedKubernetesComputer.class, computer.getClass());
    }
}
