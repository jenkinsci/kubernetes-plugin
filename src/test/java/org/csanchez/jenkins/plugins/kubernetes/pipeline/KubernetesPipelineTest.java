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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;

import hudson.model.Result;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * @author Carlos Sanchez
 */
public class KubernetesPipelineTest extends AbstractKubernetesPipelineTest {

    private static final Logger LOGGER = Logger.getLogger(KubernetesPipelineTest.class.getName());

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void runInPod() throws Exception {
        deletePods(cloud.connect(), getLabels(this), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPod.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        List<PodTemplate> templates = cloud.getAllTemplates();
        while (hasPodTemplateWithLabel("mypod",templates)) {
            LOGGER.log(Level.INFO, "Waiting for template to be created");
            templates = cloud.getAllTemplates();
            Thread.sleep(1000);
        }
        assertFalse(templates.isEmpty());
        PodTemplate template = templates.get(0);
        assertEquals(Integer.MAX_VALUE, template.getInstanceCap());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("script file contents: ", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), getLabels(this), true));
    }

    private boolean hasPodTemplateWithLabel(String label, List<PodTemplate> templates) {
        return templates != null
                && templates.stream()
                .map(PodTemplate::getLabel)
                .anyMatch(label::equals);
    }

    @Test
    public void runInPodWithDifferentShell() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithDifferentShell.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatus(Result.FAILURE,r.waitForCompletion(b));
        r.assertLogContains("/bin/bash: no such file or directory", b);
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
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "runWithEnvVariables");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithEnvVars.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        assertEnvVars(r, b);
        r.assertLogContains("OUTSIDE_CONTAINER_BUILD_NUMBER = 1\n", b);
        r.assertLogContains("INSIDE_CONTAINER_BUILD_NUMBER = 1\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_JOB_NAME = runWithEnvVariables\n", b);
        r.assertLogContains("INSIDE_CONTAINER_JOB_NAME = runWithEnvVariables\n", b);

        // check that we are getting the correct java home
        r.assertLogContains("INSIDE_JAVA_HOME =\n", b);
        r.assertLogContains("JNLP_JAVA_HOME = /usr/lib/jvm/java-1.8-openjdk\n", b);
        r.assertLogContains("JAVA7_HOME = /usr/lib/jvm/java-1.7-openjdk/jre\n", b);
        r.assertLogContains("JAVA8_HOME = /usr/lib/jvm/java-1.8-openjdk/jre\n", b);

        // check that we are not filtering too much
        r.assertLogContains("INSIDE_JAVA_HOME_X = java-home-x\n", b);
        r.assertLogContains("OUTSIDE_JAVA_HOME_X = java-home-x\n", b);
        r.assertLogContains("JNLP_JAVA_HOME_X = java-home-x\n", b);
        r.assertLogContains("JAVA7_HOME_X = java-home-x\n", b);
        r.assertLogContains("JAVA8_HOME_X = java-home-x\n", b);
    }

    @Test
    public void runWithEnvVariablesInContext() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithEnvVarsFromContext.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogNotContains("The value of FROM_ENV_DEFINITION is ABC", b);
        r.assertLogContains("The value of FROM_WITHENV_DEFINITION is DEF", b);
        r.assertLogContains("The value of WITH_QUOTE is \"WITH_QUOTE", b);
        r.assertLogContains("The value of AFTER_QUOTE is AFTER_QUOTE\"", b);
        r.assertLogContains("The value of ESCAPED_QUOTE is \\\"ESCAPED_QUOTE", b);
        r.assertLogContains("The value of AFTER_ESCAPED_QUOTE is AFTER_ESCAPED_QUOTE\\\"", b);
        r.assertLogContains("The value of SINGLE_QUOTE is BEFORE'AFTER", b);
        r.assertLogContains("The value of WITH_NEWLINE is before newline\nafter newline", b);
        r.assertLogContains("The value of WILL.NOT is ", b);
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
        r.assertLogContains("INSIDE_GLOBAL = " + GLOBAL + "\n", b);

        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR =\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR_LEGACY =\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR_FROM_SECRET =\n", b);
        r.assertLogContains("OUTSIDE_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("OUTSIDE_POD_ENV_VAR_FROM_SECRET = " + POD_ENV_VAR_FROM_SECRET_VALUE + "\n", b);
        r.assertLogContains("OUTSIDE_GLOBAL = " + GLOBAL + "\n", b);

    }

    @Test
    public void runWithOverriddenEnvVariables() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithOverriddenEnvVars.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("OUTSIDE_CONTAINER_HOME_ENV_VAR = /home/jenkins\n", b);
        r.assertLogContains("INSIDE_CONTAINER_HOME_ENV_VAR = /root\n",b);
        r.assertLogContains("OUTSIDE_CONTAINER_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_CONTAINER_POD_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n",b);
    }

    @Test
    public void supportComputerEnvVars() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("buildPropertyVars.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("OPENJDK_BUILD_NUMBER: 1\n", b);
        r.assertLogContains("JNLP_BUILD_NUMBER: 1\n", b);
        r.assertLogContains("DEFAULT_BUILD_NUMBER: 1\n", b);

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
        String overriddenNamespace = testingNamespace + "-overridden-namespace";
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        if (client.namespaces().withName(overriddenNamespace).get() == null) {
            client.namespaces().createOrReplace(
                    new NamespaceBuilder().withNewMetadata().withName(overriddenNamespace).endMetadata().build());
        }

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "overriddenNamespace");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithOverriddenNamespace.groovy"), true));

        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        NamespaceAction.push(b, overriddenNamespace);

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains(overriddenNamespace, b);
    }

    @Test
    /**
     * Step namespace should have priority over anything else.
     */
    public void runWithStepOverriddenNamespace() throws Exception {
        String overriddenNamespace = testingNamespace + "-overridden-namespace";
        String stepNamespace = testingNamespace + "-overridden-namespace2";
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        if (client.namespaces().withName(stepNamespace).get() == null) {
            client.namespaces().createOrReplace(
                    new NamespaceBuilder().withNewMetadata().withName(stepNamespace).endMetadata().build());
        }

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "stepOverriddenNamespace");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithStepOverriddenNamespace.groovy")
                .replace("OVERRIDDEN_NAMESPACE", stepNamespace), true));

        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        NamespaceAction.push(b, overriddenNamespace);

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

    @Test
    public void runWithActiveDeadlineSeconds() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "Deadline");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithActiveDeadlineSeconds.groovy")
                , true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        r.waitForMessage("podTemplate", b);

        PodTemplate deadlineTemplate = cloud.getAllTemplates().stream().filter(x -> x.getLabel() == "deadline").findAny().orElse(null);

        assertNotNull(deadlineTemplate);
        assertEquals(10, deadlineTemplate.getActiveDeadlineSeconds());
        r.assertLogNotContains("Hello from container!", b);
    }

}
