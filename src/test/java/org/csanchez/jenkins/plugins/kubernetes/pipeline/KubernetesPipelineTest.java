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

import static java.util.Arrays.*;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;
import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.URL;
import java.util.Collections;
import java.util.logging.Level;

import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerSecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.PodSecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import com.google.common.collect.ImmutableMap;

import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * @author Carlos Sanchez
 */
public class KubernetesPipelineTest {

    private static final String SECRET_KEY = "password";
    private static final String CONTAINER_ENV_VAR_VALUE = "container-env-var-value";
    private static final String POD_ENV_VAR_VALUE = "pod-env-var-value";
    private static final String CONTAINER_ENV_VAR_FROM_SECRET_VALUE = "container-pa55w0rd";
    private static final String POD_ENV_VAR_FROM_SECRET_VALUE = "pod-pa55w0rd";

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule
    public JenkinsRuleNonLocalhost r = new JenkinsRuleNonLocalhost();
    @Rule
    public LoggerRule logs = new LoggerRule().record(KubernetesCloud.class, Level.ALL);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static KubernetesCloud cloud;

    @BeforeClass
    public static void configureCloud() throws Exception {
        cloud = setupCloud();
        createSecret(cloud.connect());
    }

    @Before
    public void configureTemplates() throws Exception {
        cloud.getTemplates().clear();
        cloud.addTemplate(buildBusyboxTemplate("busybox"));
    }

    /**
     * Create a busybox template
     */
    private PodTemplate buildBusyboxTemplate(String label) {
        // Create a busybox template
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(label);

        ContainerTemplate containerTemplate = new ContainerTemplate("busybox", "busybox", "cat", "");
        containerTemplate.setTtyEnabled(true);
        podTemplate.getContainers().add(containerTemplate);
        setEnvVariables(podTemplate);
        return podTemplate;
    }

    @Before
    public void addCloudToJenkins() throws Exception {
        // Slaves running in Kubernetes (minikube) need to connect to this server, so localhost does not work
        URL url = r.getURL();

        String hostAddress = System.getProperty("jenkins.host.address");
        if (hostAddress == null) {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        }
        URL nonLocalhostUrl = new URL(url.getProtocol(), hostAddress, url.getPort(),
                url.getFile());
        JenkinsLocationConfiguration.get().setUrl(nonLocalhostUrl.toString());

        r.jenkins.clouds.add(cloud);
    }

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
        r.assertLogContains("INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = " + CONTAINER_ENV_VAR_FROM_SECRET_VALUE + "\n", b);
        r.assertLogContains("INSIDE_POD_ENV_VAR = " + POD_ENV_VAR_VALUE + "\n", b);
        r.assertLogContains("INSIDE_POD_ENV_VAR_FROM_SECRET = " + POD_ENV_VAR_FROM_SECRET_VALUE + "\n", b);

        r.assertLogContains("OUTSIDE_CONTAINER_ENV_VAR =\n", b);
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
        String overriddenNamespace = "kubernetes-plugin-overridden-namespace";
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
    public void runWithOverriddenNamespace2() throws Exception {
        String overriddenNamespace = "kubernetes-plugin-overridden-namespace";
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        client.namespaces().createOrReplace(
                new NamespaceBuilder().withNewMetadata().withName("testns2").endMetadata()
                        .build());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runWithOverriddenNamespace2.groovy"), true));

        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        NamespaceAction namespaceAction = new NamespaceAction(b);
        namespaceAction.push(overriddenNamespace);

        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("testns2", b);
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

    @Issue("JENKINS-41758")
    @Test
    public void declarative() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "job with dir");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("declarative.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
    }

    private String loadPipelineScript(String name) {
        try {
            return new String(IOUtils.toByteArray(getClass().getResourceAsStream(name)));
        } catch (Throwable t) {
            throw new RuntimeException("Could not read resource:[" + name + "].");
        }
    }

    private static void createSecret(KubernetesClient client) {
        Secret secret = new SecretBuilder()
                .withStringData(ImmutableMap.of(SECRET_KEY, CONTAINER_ENV_VAR_FROM_SECRET_VALUE)).withNewMetadata()
                .withName("container-secret").endMetadata().build();
        client.secrets().createOrReplace(secret);
        secret = new SecretBuilder().withStringData(ImmutableMap.of(SECRET_KEY, POD_ENV_VAR_FROM_SECRET_VALUE))
                .withNewMetadata().withName("pod-secret").endMetadata().build();
        client.secrets().createOrReplace(secret);
    }

    private static void setEnvVariables(PodTemplate podTemplate) {
        PodSecretEnvVar podSecretEnvVar = new PodSecretEnvVar("POD_ENV_VAR_FROM_SECRET", "pod-secret", SECRET_KEY);
        PodEnvVar podSimpleEnvVar = new PodEnvVar("POD_ENV_VAR", POD_ENV_VAR_VALUE);
        podTemplate.setEnvVars(asList(podSecretEnvVar, podSimpleEnvVar));
        ContainerEnvVar containerEnvVariable = new ContainerEnvVar("CONTAINER_ENV_VAR", CONTAINER_ENV_VAR_VALUE);
        ContainerSecretEnvVar containerSecretEnvVariable = new ContainerSecretEnvVar("CONTAINER_ENV_VAR_FROM_SECRET",
                "container-secret", SECRET_KEY);
        podTemplate.getContainers().get(0).setEnvVars(asList(containerEnvVariable, containerSecretEnvVariable));
    }
}
