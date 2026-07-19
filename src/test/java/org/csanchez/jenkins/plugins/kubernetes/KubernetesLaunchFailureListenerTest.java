/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes;

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
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.PhaseExecution;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mockito;

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

    private static KubernetesComputer spyComputerFor(KubernetesSlave agent) {
        KubernetesComputer computer = Mockito.spy(new KubernetesComputer(agent));
        // A computer whose node has not been registered with Jenkins cannot resolve getNode() on its
        // own; stub it so the listener can reach the agent (and its launcher) as it would in production.
        doReturn(agent).when(computer).getNode();
        return computer;
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
        ProvisioningActivity.Id id = agent.getId();

        // The launcher records the underlying cause before the launch is reported as failed.
        ((KubernetesLauncher) agent.getLauncher()).setProblem(new RuntimeException("pod failed to schedule"));

        KubernetesComputer computer = spyComputerFor(agent);
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
    }

    @Test
    void testHealthyLaunchRecordsNoFail() throws Exception {
        KubernetesSlave agent = provisionAgent();
        ProvisioningActivity.Id id = agent.getId();

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
