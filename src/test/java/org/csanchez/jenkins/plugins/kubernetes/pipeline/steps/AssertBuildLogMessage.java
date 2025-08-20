package org.csanchez.jenkins.plugins.kubernetes.pipeline.steps;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

public class AssertBuildLogMessage implements RealJenkinsExtension.Step {

    private final String message;
    private final RunId runId;

    public AssertBuildLogMessage(String message, RunId runId) {
        this.message = message;
        this.runId = runId;
    }

    @Override
    public void run(JenkinsRule r) throws Throwable {
        WorkflowJob p = r.jenkins.getItemByFullName(runId.name, WorkflowJob.class);
        WorkflowRun b = p.getBuildByNumber(runId.number);
        r.waitForMessage(message, b);
    }
}
