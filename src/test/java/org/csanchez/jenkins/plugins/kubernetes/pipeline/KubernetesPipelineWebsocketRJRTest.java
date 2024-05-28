package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.net.UnknownHostException;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.AssertBuildStatusSuccess;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.RunId;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.SetupCloud;
import org.junit.Test;

public class KubernetesPipelineWebsocketRJRTest extends AbstractKubernetesPipelineRJRTest {

    public KubernetesPipelineWebsocketRJRTest() throws UnknownHostException {
        super(new SetupCloud(true));
    }

    @Test
    public void basicPipeline() throws Throwable {
        RunId runId = createWorkflowJobThenScheduleRun();
        rjr.runRemotely(new AssertBuildStatusSuccess(runId));
    }
}
