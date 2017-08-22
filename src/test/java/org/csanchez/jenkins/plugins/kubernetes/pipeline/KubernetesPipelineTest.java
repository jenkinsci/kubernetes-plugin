/*
 * The MIT License
 *
 * Copyright (c) 2016, Carlos Sanchez
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
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;

import java.util.Collections;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;

import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * @author Carlos Sanchez
 */
public class KubernetesPipelineTest extends AbstractKubernetesPipelineTest {

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void runInPod() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPod.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("PID file contents: ", b);
    }

    @Test
    public void runInPodWithMultipleContainers() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithMultipleContainers.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("My Kubernetes Pipeline", b);
        r.assertLogContains("my-mount", b);
        r.assertLogContains("Apache Maven 3.3.9", b);
    }

    @Test
    public void runInPodWithExistingTemplate() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithExistingTemplate.groovy")
                , true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("outside container", b);
        r.assertLogContains("inside container", b);
    }

    @Test
    public void runWithEnvVariables() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithEnvVars.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        assertEnvVars(r, b);
    }

    @Test
    public void runWithEnvVariablesInContext() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithEnvVarsFromContext.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("The value of FROM_ENV_DEFINITION is ABC", b);
        r.assertLogContains("The value of FROM_WITHENV_DEFINITION is DEF", b);
    }

    @Test
    public void runWithExistingEnvVariables() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithExistingTemplate.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        assertEnvVars(r, b);
    }

    private void assertEnvVars(JenkinsRuleNonLocalhost r2, WorkflowRun b) throws Exception {
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR_LEGACY = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = " + CONTAINER_ENV_VAR_FROM_SECRET_VALUE + "\n", b);
        r.assertLogContains("INSIDE_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_POD_ENV_VAR_FROM_SECRET = " + POD_ENV_VAR_FROM_SECRET_VALUE + "\n", b);

        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR =\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR_LEGACY =\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR_FROM_SECRET =\n", b);
        r.assertLogContains("OUTSIDE_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("OUTSIDE_POD_ENV_VAR_FROM_SECRET = " + POD_ENV_VAR_FROM_SECRET_VALUE + "\n", b);
    }

    @Test
    public void runJobWithSpaces() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p with spaces");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runJobWithSpaces.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("pwd is -/home/jenkins/workspace/p with spaces-", b);
    }

    @Test
    public void runDirContext() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runDirContext.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        String workspace = "/home/jenkins/workspace/job with dir";
        r.assertLogContains("initpwd is -" + workspace + "-", b);
        r.assertLogContains("dirpwd is -" + workspace + "/hz-", b);
        r.assertLogContains("postpwd is -" + workspace + "-", b);

    }

    @Test
    public void runWithOverriddenNamespace() throws Exception {
        String overriddenNamespace = TESTING_NAMESPACE + "-overridden-namespace";
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        client.namespaces().createOrReplace(
                new NamespaceBuilder().withNewMetadata().withName(overriddenNamespace).endMetadata()
                        .build());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithOverriddenNamespace.groovy"), true));

        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        NamespaceAction namespaceAction = new NamespaceAction(b);
        namespaceAction.push(overriddenNamespace);

        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains(overriddenNamespace, b);
    }

    @Test
    /**
     * Step namespace should have priority over anything else.
     */
    public void runWithStepOverriddenNamespace() throws Exception {
        String overriddenNamespace = TESTING_NAMESPACE + "-overridden-namespace";
        String stepNamespace = TESTING_NAMESPACE + "-overridden-namespace2";
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        client.namespaces().createOrReplace(
                new NamespaceBuilder().withNewMetadata().withName(stepNamespace).endMetadata().build());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithStepOverriddenNamespace.groovy"), true));

        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        NamespaceAction namespaceAction = new NamespaceAction(b);
        namespaceAction.push(overriddenNamespace);

        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains(stepNamespace, b);
    }

    @Test
    public void runInPodWithLivenessProbe() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pod with liveness probe");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithLivenessProbe.groovy")
                , true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Still alive", b);
    }

    // @Test
    public void runInPodWithRestart() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                story.j.jenkins.clouds.add(new KubernetesCloud("test"));

                story.j.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1",
                        Node.Mode.NORMAL, "", story.j.createComputerLauncher(null), RetentionStrategy.NOOP,
                        Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithRestart.groovy")
                        , true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("withDisplayAfterRestart/1", b);
            }
        });
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override
            public void evaluate() throws Throwable {
                SemaphoreStep.success("withDisplayAfterRestart/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                assertNotNull(p);
                WorkflowRun b = p.getBuildByNumber(1);
                assertNotNull(b);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("DISPLAY=:", b);
                r.assertLogContains("xxx", b);
            }
        });
    }

}
