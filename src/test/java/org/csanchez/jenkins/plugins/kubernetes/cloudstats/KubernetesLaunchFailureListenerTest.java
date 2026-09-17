package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesLauncher;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.PhaseExecution;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

/**
 * Verifies the optional launch-failure listener attaches a {@code LAUNCHING:FAIL} to the cloud-stats
 * activity of a tracked Kubernetes computer, does nothing for a healthy launch, and is a safe no-op for
 * a non-Kubernetes computer (JENKINS-67256).
 */
@WithJenkins
class KubernetesLaunchFailureListenerTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    /**
     * Registers a cloud with a matching template, provisions one agent, records the PROVISIONING
     * activity the way core does once provisioning starts, and returns the (already-resolved) agent so
     * a launch outcome can be simulated against it.
     */
    private KubernetesSlave provisionAgent() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        template.setLabel("foo");
        cloud.addTemplate(template);
        r.jenkins.clouds.add(cloud);

        Label label = Label.get("foo");
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new Cloud.CloudState(label, 0), 1);
        NodeProvisioner.PlannedNode plannedNode = planned.iterator().next();
        for (CloudProvisioningListener cpl : CloudProvisioningListener.all()) {
            cpl.onStarted(cloud, label, planned);
        }
        return (KubernetesSlave) plannedNode.future.get();
    }

    private static TrackedKubernetesComputer spyComputerFor(KubernetesSlave agent) {
        TrackedKubernetesComputer computer = Mockito.spy(new TrackedKubernetesComputer(agent));
        // A computer whose node has not been registered with Jenkins cannot resolve getNode() on its
        // own; stub it so the listener can reach the agent (and its launcher) as it would in production.
        doReturn(agent).when(computer).getNode();
        return computer;
    }

    /** The tracked computer resolves the Id by node name from the recorded activities, as in production. */
    private static ProvisioningActivity.Id idFor(KubernetesSlave agent) {
        return new TrackedKubernetesComputer(agent).getId();
    }

    private static ProvisioningActivity activityFor(ProvisioningActivity.Id id) {
        // Observe through the public cloud-stats seam the plugin is expected to feed.
        return CloudStatistics.get().getActivities().stream()
                .filter(a -> a.getId().getFingerprint() == id.getFingerprint())
                .findFirst()
                .orElse(null);
    }

    @Test
    void testLaunchFailureRecordsFail() throws Exception {
        KubernetesSlave agent = provisionAgent();
        ProvisioningActivity.Id id = idFor(agent);
        assertNotNull(id, "the provisioning activity should be resolvable by node name");

        // The launcher records the underlying cause before the launch is reported as failed.
        ((KubernetesLauncher) agent.getLauncher()).setProblem(new RuntimeException("pod failed to schedule"));

        TrackedKubernetesComputer computer = spyComputerFor(agent);
        ExtensionList.lookupSingleton(KubernetesLaunchFailureListener.class)
                .onLaunchFailure(computer, TaskListener.NULL);

        ProvisioningActivity activity = activityFor(id);
        assertNotNull(activity, "the provisioning activity should be observable via getActivities()");
        assertEquals(
                ProvisioningActivity.Status.FAIL,
                activity.getStatus(),
                "a launch failure should mark the activity FAIL");

        PhaseExecution launching = activity.getPhaseExecution(ProvisioningActivity.Phase.LAUNCHING);
        assertNotNull(launching, "the FAIL must be recorded against the LAUNCHING phase, never PROVISIONING");
        assertTrue(
                launching.getAttachments().stream()
                        .anyMatch(a -> a.getStatus() == ProvisioningActivity.Status.FAIL
                                && a.getTitle().contains("pod failed to schedule")),
                "the failure reason should be attached to the LAUNCHING phase");

        PhaseExecution provisioning = activity.getPhaseExecution(ProvisioningActivity.Phase.PROVISIONING);
        assertNotNull(provisioning, "the activity should have opened in PROVISIONING before the launch");
        assertTrue(
                provisioning.getAttachments().stream()
                        .noneMatch(a -> a.getStatus() == ProvisioningActivity.Status.FAIL),
                "the FAIL must never be recorded against the PROVISIONING phase");
    }

    @Test
    void testHealthyLaunchRecordsNoFail() throws Exception {
        KubernetesSlave agent = provisionAgent();
        ProvisioningActivity.Id id = idFor(agent);

        // Mirror what cloud-stats' own OperationListener does on a healthy connect: LAUNCHING then
        // OPERATING, with no onLaunchFailure. The launch-failure listener must contribute no FAIL here.
        ProvisioningActivity activity = CloudStatistics.get().getActivityFor(id);
        assertNotNull(activity);
        activity.enterIfNotAlready(ProvisioningActivity.Phase.LAUNCHING);
        activity.enterIfNotAlready(ProvisioningActivity.Phase.OPERATING);

        ProvisioningActivity observed = activityFor(id);
        assertNotNull(observed);
        assertNotEquals(
                ProvisioningActivity.Status.FAIL,
                observed.getStatus(),
                "an agent that connected normally must not be marked FAIL");
        assertTrue(
                observed.getPhaseExecutions().values().stream()
                        .flatMap(pe -> pe.getAttachments().stream())
                        .noneMatch(a -> a.getStatus() == ProvisioningActivity.Status.FAIL),
                "no FAIL attachment should exist for a healthy launch");
    }

    @Test
    void testIgnoresNonKubernetesComputer() {
        // The listener is registered globally, so it must be a safe no-op for other clouds' agents.
        Computer other = Mockito.mock(Computer.class);
        assertDoesNotThrow(() -> ExtensionList.lookupSingleton(KubernetesLaunchFailureListener.class)
                .onLaunchFailure(other, TaskListener.NULL));
    }
}
