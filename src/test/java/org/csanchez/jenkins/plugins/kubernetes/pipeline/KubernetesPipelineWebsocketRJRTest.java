package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assumeKubernetes;

import java.net.UnknownHostException;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.AssertBuildStatusSuccess;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.CreateWorkflowJobThenScheduleRun;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.RunId;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.SetupCloud;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.RealJenkinsRule;

public class KubernetesPipelineWebsocketRJRTest extends AbstractKubernetesPipelineRJRTest{

    public KubernetesPipelineWebsocketRJRTest() throws UnknownHostException {
        super(new SetupCloud(true));
    }
    @Test
    public void basicPipeline() throws Throwable {
        rjr.runRemotely(new AssertBuildStatusSuccess(runId));
    }

}

