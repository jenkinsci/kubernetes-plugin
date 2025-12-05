package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.AssertBuildStatusSuccess;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.RunId;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.SetupCloud;
import org.junit.jupiter.api.Test;

class KubernetesPipelineWebsocketRJRTest extends AbstractKubernetesPipelineRJRTest {

    public KubernetesPipelineWebsocketRJRTest() throws Exception {
        super(new SetupCloud(true));
    }

    @Test
    void basicPipeline() throws Throwable {
        RunId runId = createWorkflowJobThenScheduleRun();
        rjr.runRemotely(new AssertBuildStatusSuccess(runId));
    }
}
