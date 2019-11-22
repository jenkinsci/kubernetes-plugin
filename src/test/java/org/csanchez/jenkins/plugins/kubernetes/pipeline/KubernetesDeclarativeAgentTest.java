/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import com.google.common.base.Predicates;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

import hudson.model.Result;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class KubernetesDeclarativeAgentTest extends AbstractKubernetesPipelineTest {

    @Rule
    public GitSampleRepoRule repoRule = new GitSampleRepoRule();

    @Issue({"JENKINS-41758", "JENKINS-57827"})
    @Test
    public void declarative() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
        FlowNode podTemplateNode = new DepthFirstScanner().findFirstMatch(b.getExecution(), Predicates.and(new NodeStepTypePredicate("podTemplate"), FlowScanningUtils.hasActionPredicate(ArgumentsAction.class)));
        assertNotNull("recorded arguments for podTemplate", podTemplateNode);
        Map<String, Object> arguments = podTemplateNode.getAction(ArgumentsAction.class).getArguments();
        @SuppressWarnings("unchecked")
        List<UninstantiatedDescribable> containers = (List<UninstantiatedDescribable>) arguments.get("containers");
        assertNotNull(containers);
        assertFalse("no junk in arguments: " + arguments, containers.get(0).getArguments().containsKey("alwaysPullImage"));
        FlowNode containerNode = new DepthFirstScanner().findFirstMatch(b.getExecution(), Predicates.and(new NodeStepTypePredicate("container"), FlowScanningUtils.hasActionPredicate(ArgumentsAction.class)));
        assertNotNull("recorded arguments for container", containerNode);
    }

    @Issue("JENKINS-48135")
    @Test
    public void declarativeFromYaml() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR = jnlp\n", b);
        r.assertLogContains("MAVEN_CONTAINER_ENV_VAR = maven\n", b);
        r.assertLogContains("BUSYBOX_CONTAINER_ENV_VAR = busybox\n", b);
    }

    @Issue("JENKINS-51610")
    @Test
    public void declarativeWithNamespaceFromYaml() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR = jnlp\n", b);
        r.assertLogContains("MAVEN_CONTAINER_ENV_VAR = maven\n", b);
        r.assertLogContains("BUSYBOX_CONTAINER_ENV_VAR = busybox\n", b);
    }

    @Issue("JENKINS-52259")
    @Test
    public void declarativeFromYamlFile() throws Exception {
        repoRule.init();
        repoRule.write("Jenkinsfile", loadPipelineDefinition());
        repoRule.write("declarativeYamlFile.yml", loadPipelineScript("declarativeYamlFile.yml"));
        repoRule.git("add", "Jenkinsfile");
        repoRule.git("add", "declarativeYamlFile.yml");
        repoRule.git("commit", "--message=files");

        p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(repoRule.toString()).createSCM(), "Jenkinsfile"));
        b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR = jnlp\n", b);
        r.assertLogContains("MAVEN_CONTAINER_ENV_VAR = maven\n", b);
        r.assertLogContains("BUSYBOX_CONTAINER_ENV_VAR = busybox\n", b);
    }

    @Issue("JENKINS-52623")
    @Test
    public void declarativeSCMVars() throws Exception {
        p = r.jenkins.createProject(WorkflowJob.class, "job with repo");
        // We can't use a local GitSampleRepoRule for this because the repo has to be accessible from within the container.
        p.setDefinition(new CpsScmFlowDefinition(new GitStep("https://github.com/abayer/jenkins-52623.git").createSCM(), "Jenkinsfile"));
        b = r.buildAndAssertSuccess(p);
        r.assertLogContains("Outside container: GIT_BRANCH is origin/master", b);
        r.assertLogContains("In container: GIT_BRANCH is origin/master", b);
    }

    @Issue("JENKINS-53817")
    @Test
    public void declarativeCustomWorkspace() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("Workspace dir is", b);
    }

    @Issue("JENKINS-57548")
    @Test
    public void declarativeWithNestedExplicitInheritance() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogNotContains("go version go1.6.3", b);
    }

    @Test
    public void declarativeWithNonexistentDockerImage() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        r.assertLogContains("ERROR: Unable to pull Docker image", b);
    }

    @Test
    public void declarativeWithNonexistentDockerImageLongLabel() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        r.assertLogContains("ERROR: Unable to pull Docker image", b);

    }

}
