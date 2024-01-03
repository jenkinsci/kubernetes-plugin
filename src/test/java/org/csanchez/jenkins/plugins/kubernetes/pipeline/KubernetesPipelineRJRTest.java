package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.net.UnknownHostException;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.AssertBuildStatusSuccess;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.SetupCloud;
import org.junit.Test;

public class KubernetesPipelineRJRTest extends AbstractKubernetesPipelineRJRTest {
    public KubernetesPipelineRJRTest() throws UnknownHostException {
        super(new SetupCloud());
    }

    @Test
    public void basicPipeline() throws Throwable {
        rjr.runRemotely(new AssertBuildStatusSuccess(runId));
    }
}
