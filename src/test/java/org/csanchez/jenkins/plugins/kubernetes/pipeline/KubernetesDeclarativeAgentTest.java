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

import static org.junit.Assert.*;

import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

public class KubernetesDeclarativeAgentTest extends AbstractKubernetesPipelineTest {
    @Rule
    public GitSampleRepoRule repoRule = new GitSampleRepoRule();

    @Issue("JENKINS-41758")
    @Test
    public void declarative() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("declarative.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
    }

    @Issue("JENKINS-48135")
    @Test
    public void declarativeFromYaml() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("declarativeFromYaml.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
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
        repoRule.write("Jenkinsfile", loadPipelineScript("declarativeFromYamlFile.groovy"));
        repoRule.write("declarativeYamlFile.yml", loadPipelineScript("declarativeYamlFile.yml"));
        repoRule.git("add", "Jenkinsfile");
        repoRule.git("add", "declarativeYamlFile.yml");
        repoRule.git("commit", "--message=files");

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsScmFlowDefinition(new GitStep(repoRule.toString()).createSCM(), "Jenkinsfile"));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR = jnlp\n", b);
        r.assertLogContains("MAVEN_CONTAINER_ENV_VAR = maven\n", b);
        r.assertLogContains("BUSYBOX_CONTAINER_ENV_VAR = busybox\n", b);
    }
}
