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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.slaves.SlaveComputer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;

import hudson.model.Result;
import java.util.Locale;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

/**
 * @author Carlos Sanchez
 */
public class KubernetesPipelineTest extends AbstractKubernetesPipelineTest {

    private static final Logger LOGGER = Logger.getLogger(KubernetesPipelineTest.class.getName());

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
        logs.capture(1000);
        assertNotNull(createJobThenScheduleRun());
    }

    @Test
    public void runInPod() throws Exception {
        SemaphoreStep.waitForStart("podTemplate/1", b);
        List<PodTemplate> templates = podTemplatesWithLabel(name.getMethodName(), cloud.getAllTemplates());
        assertThat(templates, hasSize(1));
        SemaphoreStep.success("podTemplate/1", null);

        // check if build failed
        assertTrue("Build has failed early: " + b.getResult(), b.isBuilding() || Result.SUCCESS.equals(b.getResult()));

        LOGGER.log(Level.INFO, "Found templates with label runInPod: {0}", templates);
        for (PodTemplate template : cloud.getAllTemplates()) {
            LOGGER.log(Level.INFO, "Cloud template \"{0}\" labels: {1}",
                    new Object[] { template.getName(), template.getLabelSet() });
        }

        Map<String, String> labels = getLabels(cloud, this, name);
        SemaphoreStep.waitForStart("pod/1", b);
        PodList pods = cloud.connect().pods().withLabels(labels).list();
        assertThat(
                "Expected one pod with labels " + labels + " but got: "
                        + pods.getItems().stream().map(pod -> pod.getMetadata()).collect(Collectors.toList()),
                pods.getItems(), hasSize(1));
        SemaphoreStep.success("pod/1", null);

        for (String msg : logs.getMessages()) {
            System.out.println(msg);
        }

        PodTemplate template = templates.get(0);
        List<PodAnnotation> annotations = template.getAnnotations();
        assertNotNull(annotations);
        boolean foundBuildUrl=false;
        for(PodAnnotation pd : annotations)
        {
            if(pd.getKey().equals("buildUrl"))
            {
                assertTrue(pd.getValue().contains(p.getUrl()));
                foundBuildUrl=true;
            }
        }
        assertTrue(foundBuildUrl);
        assertEquals(Integer.MAX_VALUE, template.getInstanceCap());
        assertThat(template.getLabelsMap(), hasEntry("jenkins/" + name.getMethodName(), "true"));

        Pod pod = pods.getItems().get(0);
        LOGGER.log(Level.INFO, "One pod found: {0}", pod);
        assertThat(pod.getMetadata().getLabels(), hasEntry("jenkins", "slave"));
        assertThat("Pod labels are wrong: " + pod, pod.getMetadata().getLabels(), hasEntry("jenkins/" + name.getMethodName(), "true"));

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("script file contents: ", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), getLabels(cloud, this, name), true));
    }

    @Test
    public void runIn2Pods() throws Exception {
        SemaphoreStep.waitForStart("podTemplate1/1", b);
        String label1 = name.getMethodName() + "-1";
        PodTemplate template1 = podTemplatesWithLabel(label1, cloud.getAllTemplates()).get(0);
        SemaphoreStep.success("podTemplate1/1", null);
        assertEquals(Integer.MAX_VALUE, template1.getInstanceCap());
        assertThat(template1.getLabelsMap(), hasEntry("jenkins/" + label1, "true"));
        SemaphoreStep.waitForStart("pod1/1", b);
        Map<String, String> labels1 = getLabels(cloud, this, name);
        labels1.put("jenkins/"+label1, "true");
        PodList pods = cloud.connect().pods().withLabels(labels1).list();
        assertTrue(!pods.getItems().isEmpty());
        SemaphoreStep.success("pod1/1", null);

        SemaphoreStep.waitForStart("podTemplate2/1", b);
        String label2 = name.getMethodName() + "-2";
        PodTemplate template2 = podTemplatesWithLabel(label2, cloud.getAllTemplates()).get(0);
        SemaphoreStep.success("podTemplate2/1", null);
        assertEquals(Integer.MAX_VALUE, template2.getInstanceCap());
        assertThat(template2.getLabelsMap(), hasEntry("jenkins/" + label2, "true"));
        assertNull(label2 + " should not inherit from anything", template2.getInheritFrom());
        SemaphoreStep.waitForStart("pod2/1", b);
        Map<String, String> labels2 = getLabels(cloud, this, name);
        labels1.put("jenkins/" + label2, "true");
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

    @Issue("JENKINS-57893")
    @Test
    public void runInPodFromYaml() throws Exception {
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
        r.assertLogNotContains(CONTAINER_ENV_VAR_FROM_SECRET_VALUE, b);
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = ******** or " + CONTAINER_ENV_VAR_FROM_SECRET_VALUE.toUpperCase(Locale.ROOT) + "\n", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), getLabels(cloud, this, name), true));
    }

    @Test
    public void runInPodWithDifferentShell() throws Exception {
        r.assertBuildStatus(Result.FAILURE,r.waitForCompletion(b));
        /* TODO instead the program fails with a IOException: Pipe closed from ContainerExecDecorator.doExec:
        r.assertLogContains("/bin/bash: no such file or directory", b);
        */
    }

    @Test
    public void bourneShellElsewhereInPath() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("/kaniko:/busybox", b);
    }

    @Test
    public void runInPodWithMultipleContainers() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("image: \"jenkins/jnlp-slave:3.10-1-alpine\"", b);
        r.assertLogContains("image: \"maven:3.3.9-jdk-8-alpine\"", b);
        r.assertLogContains("image: \"golang:1.6.3-alpine\"", b);
        r.assertLogContains("My Kubernetes Pipeline", b);
        r.assertLogContains("my-mount", b);
        r.assertLogContains("Apache Maven 3.3.9", b);
    }

    @Test
    public void runInPodNested() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("image: \"maven:3.3.9-jdk-8-alpine\"", b);
        r.assertLogContains("image: \"golang:1.6.3-alpine\"", b);
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogContains("go version go1.6.3", b);
    }

    @Issue("JENKINS-57548")
    @Test
    public void runInPodNestedExplicitInherit() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("image: \"maven:3.3.9-jdk-8-alpine\"", b);
        r.assertLogNotContains("image: \"golang:1.6.3-alpine\"", b);
        r.assertLogContains("Apache Maven 3.3.9", b);
        r.assertLogNotContains("go version go1.6.3", b);
    }

    @Issue("JENKINS-57893")
    @Test
    public void runInPodWithExistingTemplate() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("outside container", b);
        r.assertLogContains("inside container", b);
        assertEnvVars(r, b);
    }

    @Issue("JENKINS-57893")
    @Test
    public void runWithEnvVariables() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        assertEnvVars(r, b);
        r.assertLogContains("OUTSIDE_CONTAINER_BUILD_NUMBER = 1\n", b);
        r.assertLogContains("INSIDE_CONTAINER_BUILD_NUMBER = 1\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_JOB_NAME = " + getProjectName() + "\n", b);
        r.assertLogContains("INSIDE_CONTAINER_JOB_NAME = " + getProjectName() +"\n", b);

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

    private void assertEnvVars(JenkinsRuleNonLocalhost r2, WorkflowRun b) throws Exception {
        r.assertLogNotContains(POD_ENV_VAR_FROM_SECRET_VALUE, b);
        r.assertLogNotContains(CONTAINER_ENV_VAR_FROM_SECRET_VALUE, b);

        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR_LEGACY = " + CONTAINER_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = ******** or " + CONTAINER_ENV_VAR_FROM_SECRET_VALUE.toUpperCase(Locale.ROOT) + "\n", b);
        r.assertLogContains("INSIDE_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_POD_ENV_VAR_FROM_SECRET = ******** or " + POD_ENV_VAR_FROM_SECRET_VALUE.toUpperCase(Locale.ROOT) + "\n", b);
        r.assertLogContains("INSIDE_GLOBAL = " + GLOBAL + "\n", b);

        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR =\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR_LEGACY =\n", b);
        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR_FROM_SECRET = or\n", b);
        r.assertLogContains("OUTSIDE_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("OUTSIDE_POD_ENV_VAR_FROM_SECRET = ******** or " + POD_ENV_VAR_FROM_SECRET_VALUE.toUpperCase(Locale.ROOT) + "\n", b);
        r.assertLogContains("OUTSIDE_GLOBAL = " + GLOBAL + "\n", b);
    }

    @Test
    public void runWithOverriddenEnvVariables() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("OUTSIDE_CONTAINER_HOME_ENV_VAR = /home/jenkins\n", b);
        r.assertLogContains("INSIDE_CONTAINER_HOME_ENV_VAR = /root\n",b);
        r.assertLogContains("OUTSIDE_CONTAINER_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_CONTAINER_POD_ENV_VAR = " + CONTAINER_ENV_VAR_VALUE + "\n",b);
    }

    @Test
    public void supportComputerEnvVars() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("OPENJDK_BUILD_NUMBER: 1\n", b);
        r.assertLogContains("JNLP_BUILD_NUMBER: 1\n", b);
        r.assertLogContains("DEFAULT_BUILD_NUMBER: 1\n", b);

    }

    @Test
    public void runDirContext() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        String workspace = "/home/jenkins/workspace/" + getProjectName();
        r.assertLogContains("initpwd is -" + workspace + "-", b);
        r.assertLogContains("dirpwd is -" + workspace + "/hz-", b);
        r.assertLogContains("postpwd is -" + workspace + "-", b);
    }

    @Test
    public void runInPodWithLivenessProbe() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Still alive", b);
    }

    @Test
    public void runWithActiveDeadlineSeconds() throws Exception {
        SemaphoreStep.waitForStart("podTemplate/1", b);
        PodTemplate deadlineTemplate = cloud.getAllTemplates().stream().filter(x -> name.getMethodName().equals(x.getLabel())).findAny().orElse(null);
        assertNotNull(deadlineTemplate);
        SemaphoreStep.success("podTemplate/1", null);
        assertEquals(10, deadlineTemplate.getActiveDeadlineSeconds());
        r.assertLogNotContains("Hello from container!", b);
    }

    @Test
    public void runInPodWithRetention() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        assertTrue(deletePods(cloud.connect(), getLabels(this, name), true));
    }

    @Issue("JENKINS-49707")
    @Test
    public void terminatedPod() throws Exception {
        r.waitForMessage("+ sleep", b);
        deletePods(cloud.connect(), getLabels(this, name), false);
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        r.waitForMessage(new ExecutorStepExecution.RemovedNodeCause().getShortDescription(), b);
    }

    @Issue("JENKINS-58306")
    @Test
    public void cascadingDelete() throws Exception {
        try {
            cloud.connect().apps().deployments().withName("cascading-delete").delete();
        } catch (KubernetesClientException x) {
            // Failure executing: DELETE at: https://…/apis/apps/v1/namespaces/kubernetes-plugin-test/deployments/cascading-delete. Message: Forbidden!Configured service account doesn't have access. Service account may have been revoked. deployments.apps "cascading-delete" is forbidden: User "system:serviceaccount:…:…" cannot delete resource "deployments" in API group "apps" in the namespace "kubernetes-plugin-test".
            assumeNoException("was not permitted to clean up any previous deployment, so presumably cannot run test either", x);
        }
        cloud.connect().apps().replicaSets().withLabel("app", "cascading-delete").delete();
        cloud.connect().pods().withLabel("app", "cascading-delete").delete();
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
    }

    @Test
    public void computerCantBeConfigured() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        SemaphoreStep.waitForStart("pod/1", b);
        Optional<KubernetesSlave> optionalNode = r.jenkins.getNodes().stream().filter(KubernetesSlave.class::isInstance).map(KubernetesSlave.class::cast).findAny();
        assertTrue(optionalNode.isPresent());
        KubernetesSlave node = optionalNode.get();

        JenkinsRule.WebClient wc = r.createWebClient().login("admin");
        wc.getOptions().setPrintContentOnFailingStatusCode(false);

        HtmlPage nodeIndex = wc.getPage(node);
        assertNotXPath(nodeIndex, "//*[text() = 'configure']");
        wc.assertFails(node.toComputer().getUrl()+"configure", 403);
        SemaphoreStep.success("pod/1", null);
    }

    private void assertNotXPath(HtmlPage page, String xpath) {
        HtmlElement documentElement = page.getDocumentElement();
        assertNull("There should not be an object that matches XPath:" + xpath, DomNodeUtil.selectSingleNode(documentElement, xpath));
    }
  
    @Issue("JENKINS-57717")
    @Test
    public void runInPodWithShowRawYamlFalse() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogNotContains("value: \"container-env-var-value\"", b);
    }

    @Issue("JENKINS-58574")
    @Test
    public void showRawYamlFalseInherited() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogNotContains("value: \"container-env-var-value\"", b);
    }

    @Test
    @Issue("JENKINS-58405")
    public void overrideYaml() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
    }

    @Test
    @Issue("JENKINS-58405")
    public void mergeYaml() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
    }

    @Test
    @Issue("JENKINS-58602")
    public void jenkinsSecretHidden() throws Exception {
        SemaphoreStep.waitForStart("pod/1", b);
        Optional<SlaveComputer> scOptional = Arrays.stream(r.jenkins.getComputers())
                .filter(SlaveComputer.class::isInstance)
                .map(SlaveComputer.class::cast)
                .findAny();
        assertTrue(scOptional.isPresent());
        String jnlpMac = scOptional.get().getJnlpMac();
        SemaphoreStep.success("pod/1", b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogNotContains(jnlpMac, b);
    }
}
