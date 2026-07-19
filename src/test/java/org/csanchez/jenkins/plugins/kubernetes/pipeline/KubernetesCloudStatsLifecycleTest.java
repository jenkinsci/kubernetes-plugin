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

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.awaitility.Awaitility.await;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.PhaseExecution;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity.Phase;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity.Status;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * Cluster-gated proof that a healthy Kubernetes agent is tracked cleanly through the whole
 * cloud-stats lifecycle. Nothing here wires cloud-stats by hand: the tracked identity minted when the
 * agent is provisioned, cloud-stats' own {@code OperationListener} (which advances {@code LAUNCHING}
 * and {@code OPERATING} from computer lifecycle events) and its {@code SlaveCompletionDetector} (which
 * drives {@code COMPLETED} on node removal) do all the work.
 *
 * <p>Skipped rather than failed when no cluster is configured, via {@code assumeKubernetes()} in
 * {@link AbstractKubernetesPipelineTest}.
 */
class KubernetesCloudStatsLifecycleTest extends AbstractKubernetesPipelineTest {

    @AfterEach
    void tearDown() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), true);
    }

    @Issue("JENKINS-67256")
    @Test
    void healthyAgentLifecycle() throws Exception {
        WorkflowRun run = createJobThenScheduleRun();
        r.assertBuildStatusSuccess(r.waitForCompletion(run));

        // The single-use agent is removed once the build finishes, and only then does
        // SlaveCompletionDetector drive the activity to COMPLETED. This is the only cloud in play, so
        // a healthy attempt must leave exactly one activity — waiting on that also proves the plugin
        // never jumped straight to COMPLETED before the agent operated.
        await().atMost(Duration.ofMinutes(2)).until(() -> {
            List<ProvisioningActivity> activities = CloudStatistics.get().getActivities();
            return activities.size() == 1 && activities.get(0).getCurrentPhase() == Phase.COMPLETED;
        });

        List<ProvisioningActivity> activities = CloudStatistics.get().getActivities();
        assertThat("a healthy attempt should record exactly one activity", activities, hasSize(1));
        ProvisioningActivity activity = activities.get(0);

        // The lone activity must be the one minted for this cloud's provisioning attempt, not merely the
        // only one around: planned node, launched agent and activity share the tracking identity.
        assertEquals(
                cloud.name, activity.getId().getCloudName(), "the activity should belong to the tracked attempt");

        // Full ordered traversal with a real timestamp recorded for every phase.
        long previousTimestamp = 0;
        for (Phase phase : List.of(Phase.PROVISIONING, Phase.LAUNCHING, Phase.OPERATING, Phase.COMPLETED)) {
            PhaseExecution execution = activity.getPhaseExecution(phase);
            assertNotNull(execution, "the activity should have traversed the " + phase + " phase");
            assertThat("the " + phase + " phase should carry a timing", execution.getStartedTimestamp(), greaterThan(0L));
            assertTrue(
                    execution.getStartedTimestamp() >= previousTimestamp,
                    "phases should be entered in PROVISIONING -> LAUNCHING -> OPERATING -> COMPLETED order");
            previousTimestamp = execution.getStartedTimestamp();
        }

        assertEquals(Status.OK, activity.getStatus(), "a healthy agent's activity should be OK");

        List<String> attachmentTitles = activity.getPhaseExecutions().values().stream()
                .flatMap(execution -> execution.getAttachments().stream())
                .map(attachment -> attachment.getTitle())
                .collect(Collectors.toList());
        assertTrue(
                attachmentTitles.stream()
                        .noneMatch(title -> ProvisioningActivity.PREMATURE_COMPLETION_DETECTED.equals(title)),
                "a healthy agent must not be flagged with a premature-completion attachment, but got: "
                        + attachmentTitles);
    }
}
