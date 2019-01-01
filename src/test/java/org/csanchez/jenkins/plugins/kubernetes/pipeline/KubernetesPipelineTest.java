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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;

import hudson.model.Result;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * @author Carlos Sanchez
 */
public class KubernetesPipelineTest extends AbstractKubernetesPipelineTest {

    private static final Logger LOGGER = Logger.getLogger(KubernetesPipelineTest.class.getName());

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public TestName name = new TestName();

    @Override
    protected TestName getTestName() {
        return name;
    }

    @Test
    public void runInPod() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript(name.getMethodName() + ".groovy"), true));

        logs.capture(1000);
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        List<PodTemplate> templates = null;
        while (b.isBuilding() && (templates = podTemplatesWithLabel(name.getMethodName(), cloud.getAllTemplates())).isEmpty()) {
            LOGGER.log(Level.INFO, "Waiting for runInPod template to be created");
            Thread.sleep(1000);
        }

        // check if build failed
        assertTrue("Build has failed early: " + b.getResult(), b.isBuilding() || Result.SUCCESS.equals(b.getResult()));

        LOGGER.log(Level.INFO, "Found templates with label runInPod: {0}", templates);
        for (PodTemplate template : cloud.getAllTemplates()) {
            LOGGER.log(Level.INFO, "Cloud template \"{0}\" labels: {1}",
                    new Object[] { template.getName(), template.getLabelSet() });
        }

        Map<String, String> labels = getLabels(cloud, this, name);
        PodList pods = new PodListBuilder().withItems(Collections.emptyList()).build();
        while (pods.getItems().isEmpty()) {
            LOGGER.log(Level.INFO, "Waiting for pods to be created with labels: {0}", labels);
            pods = cloud.connect().pods().withLabels(labels).list();
            Thread.sleep(1000);
        }

        for (String msg : logs.getMessages()) {
            System.out.println(msg);
        }

        assertThat(templates, hasSize(1));
        PodTemplate template = templates.get(0);
        assertEquals(Integer.MAX_VALUE, template.getInstanceCap());
        assertThat(template.getLabelsMap(), hasEntry("jenkins/" + name.getMethodName(), "true"));

        assertThat(
                "Expected one pod with labels " + labels + " but got: "
                        + pods.getItems().stream().map(pod -> pod.getMetadata()).collect(Collectors.toList()),
                pods.getItems(), hasSize(1));
        Pod pod = pods.getItems().get(0);
        LOGGER.log(Level.INFO, "One pod found: {0}", pod);
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins/" + name.getMethodName(), "true"));

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("script file contents: ", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), getLabels(cloud, this, name), true));
    }

    @Test
    public void runIn2Pods() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript(name.getMethodName() + ".groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        SemaphoreStep.waitForStart("podTemplate1/1", b);
        PodTemplate template1 = podTemplatesWithLabel("mypod", cloud.getAllTemplates()).get(0);
        SemaphoreStep.success("podTemplate1/1", null);
        assertEquals(Integer.MAX_VALUE, template1.getInstanceCap());
        assertThat(template1.getLabelsMap(), hasEntry("jenkins/mypod", "true"));
        SemaphoreStep.waitForStart("pod1/1", b);
        Map<String, String> labels1 = getLabels(cloud, this, name);
        labels1.put("jenkins/mypod", "true");
        PodList pods = cloud.connect().pods().withLabels(labels1).list();
        assertTrue(!pods.getItems().isEmpty());
        SemaphoreStep.success("pod1/1", null);

        SemaphoreStep.waitForStart("podTemplate2/1", b);
        PodTemplate template2 = podTemplatesWithLabel("mypod2", cloud.getAllTemplates()).get(0);
        SemaphoreStep.success("podTemplate2/1", null);
        assertEquals(Integer.MAX_VALUE, template2.getInstanceCap());
        assertThat(template2.getLabelsMap(), hasEntry("jenkins/mypod2", "true"));
        assertNull("mypod2 should not inherit from anything", template2.getInheritFrom());
        SemaphoreStep.waitForStart("pod2/1", b);
        Map<String, String> labels2 = getLabels(cloud, this, name);
        labels1.put("jenkins/mypod2", "true");
        PodList pods2 = cloud.connect().pods().withLabels(labels2).list();
        assertTrue(!pods2.getItems().isEmpty());
        SemaphoreStep.success("pod2/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("script file contents: ", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), getLabels(cloud, this, name), true));
    }

    private List<PodTemplate> podTemplatesWithLabel(String label, List<PodTemplate> templates) {
        return templates.stream().filter(t -> label.equals(t.getLabel())).collect(Collectors.toList());
    }

    @Test
    public void runInPodFromYaml() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodFromYaml.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        List<PodTemplate> templates = cloud.getTemplates();
        while (templates.isEmpty()) {
            LOGGER.log(Level.INFO, "Waiting for template to be created");
            templates = cloud.getTemplates();
            Thread.sleep(1000);
        }
        assertFalse(templates.isEmpty());
        PodTemplate template = templates.get(0);
        assertEquals(Integer.MAX_VALUE, template.getInstanceCap());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("script file contents: ", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), getLabels(cloud, this, name), true));
    }

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
        r.assertLogContains("[jnlp] jenkins/jnlp-slave:3.10-1-alpine", b);
        r.assertLogContains("[maven] maven:3.3.9-jdk-8-alpine", b);
        r.assertLogContains("[golang] golang:1.6.3-alpine", b);
        r.assertLogContains("My Kubernetes Pipeline", b);
        r.assertLogContains("my-mount", b);
        r.assertLogContains("Apache Maven 3.3.9", b);
    }

    @Test
    public void runInPodNested() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodNested.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("[maven] maven:3.3.9-jdk-8-alpine", b);
        r.assertLogContains("[golang] golang:1.6.3-alpine", b);
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("go version go1.6.3", b);
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
        r.assertLogContains("The initial value of POD_ENV_VAR is pod-env-var-value", b);
        r.assertLogContains("The value of POD_ENV_VAR outside container is /bin/mvn:pod-env-var-value", b);
        r.assertLogContains("The value of FROM_ENV_DEFINITION is ABC", b);
        r.assertLogContains("The value of FROM_WITHENV_DEFINITION is DEF", b);
        r.assertLogContains("The value of WITH_QUOTE is \"WITH_QUOTE", b);
        r.assertLogContains("The value of AFTER_QUOTE is AFTER_QUOTE\"", b);
        r.assertLogContains("The value of ESCAPED_QUOTE is \\\"ESCAPED_QUOTE", b);
        r.assertLogContains("The value of AFTER_ESCAPED_QUOTE is AFTER_ESCAPED_QUOTE\\\"", b);
        r.assertLogContains("The value of SINGLE_QUOTE is BEFORE'AFTER", b);
        r.assertLogContains("The value of WITH_NEWLINE is before newline\nafter newline", b);
        r.assertLogContains("The value of POD_ENV_VAR is /bin/mvn:pod-env-var-value", b);
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
    public void runWithCloudOverriddenNamespace() throws Exception {
        String overriddenNamespace = testingNamespace + "-overridden-namespace";
        cloud.setNamespace(overriddenNamespace);
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        if (client.namespaces().withName(overriddenNamespace).get() == null) {
            client.namespaces().createOrReplace(
                    new NamespaceBuilder().withNewMetadata().withName(overriddenNamespace).endMetadata().build());
        }

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "overriddenNamespace");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript(name.getMethodName()+".groovy"), true));

        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

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
        cloud.setNamespace(overriddenNamespace);
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        if (client.namespaces().withName(stepNamespace).get() == null) {
            client.namespaces().createOrReplace(
                    new NamespaceBuilder().withNewMetadata().withName(stepNamespace).endMetadata().build());
        }

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "stepOverriddenNamespace");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript(name.getMethodName()+".groovy")
                .replace("OVERRIDDEN_NAMESPACE", stepNamespace), true));

        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
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

    @Test
    public void runWithActiveDeadlineSeconds() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "Deadline");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithActiveDeadlineSeconds.groovy")
                , true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        r.waitForMessage("podTemplate", b);

        PodTemplate deadlineTemplate = cloud.getAllTemplates().stream().filter(x -> x.getLabel() == "runWithActiveDeadlineSeconds").findAny().orElse(null);

        assertNotNull(deadlineTemplate);
        assertEquals(10, deadlineTemplate.getActiveDeadlineSeconds());
        r.assertLogNotContains("Hello from container!", b);
    }

    @Test
    public void runInPodWithRetention() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pod with retention");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithRetention.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        assertTrue(deletePods(cloud.connect(), getLabels(this, name), true));
    }
}
