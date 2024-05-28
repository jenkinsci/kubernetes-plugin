package org.csanchez.jenkins.plugins.kubernetes.pipeline.steps;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

/**
 * Creates a workflow job using the specified script, then schedules it and returns a reference to the run.
 */
public class CreateWorkflowJobThenScheduleTask implements RealJenkinsRule.Step2<RunId> {
    private String script;

    public CreateWorkflowJobThenScheduleTask(String script) {
        this.script = script;
    }

    @Override
    public RunId run(JenkinsRule r) throws Throwable {
        WorkflowJob project = r.createProject(WorkflowJob.class);
        project.setDefinition(new CpsFlowDefinition(script, true));
        project.save();
        System.out.println("Scheduling build...");
        WorkflowRun b = project.scheduleBuild2(0).getStartCondition().get();
        System.out.println("Build scheduled...");
        return new RunId(project.getFullName(), b.number);
    }
}
