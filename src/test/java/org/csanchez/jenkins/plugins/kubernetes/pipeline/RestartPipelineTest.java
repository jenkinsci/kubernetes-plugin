/*
 * The MIT License
 *
 * Copyright (c) 2017, Red Hat, Inc.
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

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsNonLocalhostRule;

import hudson.model.Node;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;

public class RestartPipelineTest {
    protected static final String CONTAINER_ENV_VAR_VALUE = "container-env-var-value";
    protected static final String POD_ENV_VAR_VALUE = "pod-env-var-value";
    protected static final String SECRET_KEY = "password";
    protected static final String CONTAINER_ENV_VAR_FROM_SECRET_VALUE = "container-pa55w0rd";
    protected static final String POD_ENV_VAR_FROM_SECRET_VALUE = "pod-pa55w0rd";
    protected KubernetesCloud cloud;

    @Rule
    public RestartableJenkinsNonLocalhostRule story = new RestartableJenkinsNonLocalhostRule(44000);
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public LoggerRule logs = new LoggerRule().record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()),
           Level.ALL);
    //.record("org.jenkinsci.plugins.durabletask", Level.ALL).record("org.jenkinsci.plugins.workflow.support.concurrent", Level.ALL).record("org.csanchez.jenkins.plugins.kubernetes.pipeline", Level.ALL);

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void isKubernetesConfigured() throws Exception {
        assumeKubernetes();
    }

    private static void setEnvVariables(PodTemplate podTemplate) {
        TemplateEnvVar podSecretEnvVar = new SecretEnvVar("POD_ENV_VAR_FROM_SECRET", "pod-secret", SECRET_KEY);
        TemplateEnvVar podSimpleEnvVar = new KeyValueEnvVar("POD_ENV_VAR", POD_ENV_VAR_VALUE);
        podTemplate.setEnvVars(asList(podSecretEnvVar, podSimpleEnvVar));
        TemplateEnvVar containerEnvVariable = new KeyValueEnvVar("CONTAINER_ENV_VAR", CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerEnvVariableLegacy = new ContainerEnvVar("CONTAINER_ENV_VAR_LEGACY",
                CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerSecretEnvVariable = new SecretEnvVar("CONTAINER_ENV_VAR_FROM_SECRET",
                "container-secret", SECRET_KEY);
        podTemplate.getContainers().get(0)
                .setEnvVars(asList(containerEnvVariable, containerEnvVariableLegacy, containerSecretEnvVariable));
    }

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

    public void configureCloud() throws Exception {
        cloud = setupCloud(this, name);
        createSecret(cloud.connect(), cloud.getNamespace());
        cloud.getTemplates().clear();
        cloud.addTemplate(buildBusyboxTemplate("busybox"));

        setupHost();

        story.j.jenkins.clouds.add(cloud);
    }
    
    public void configureAgentListener() throws IOException {
      //Take random port and fix it, to be the same after Jenkins restart
      int fixedPort = story.j.jenkins.getTcpSlaveAgentListener().getAdvertisedPort();
      story.j.jenkins.setSlaveAgentPort(fixedPort);
    }

    protected String loadPipelineScript(String name) {
        try {
            return new String(IOUtils.toByteArray(getClass().getResourceAsStream(name)));
        } catch (Throwable t) {
            throw new RuntimeException("Could not read resource:[" + name + "].");
        }
    }

    @Test
    public void runInPodWithRestartWithMultipleContainerCalls() throws Exception {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            r.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1",
                    Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP,
                    Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            // we need to wait until we are sure that the sh
            // step has started...
            r.waitForMessage("+ sleep 5", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins.getItemByFullName(projectName.get(), WorkflowJob.class).getBuildByNumber(1);
            r.assertLogContains("finished the test!", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Test
    public void runInPodWithRestartWithLongSleep() throws Exception {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            r.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1",
                    Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP,
                    Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            // we need to wait until we are sure that the sh
            // step has started...
            r.waitForMessage("+ sleep 5", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins.getItemByFullName(projectName.get(), WorkflowJob.class).getBuildByNumber(1);
            r.assertLogContains("finished the test!", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Test
    public void windowsRestart() throws Exception {
        assumeWindows();
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            cloud.setDirectConnection(false);
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            r.waitForMessage("sleeping #0", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins.getItemByFullName(projectName.get(), WorkflowJob.class).getBuildByNumber(1);
            r.assertLogContains("sleeping #9", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
            r.assertLogContains("finished the test!", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-49707")
    @Test
    public void terminatedPodAfterRestart() throws Exception {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            r.waitForMessage("+ sleep", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins.getItemByFullName(projectName.get(), WorkflowJob.class).getBuildByNumber(1);
            r.waitForMessage("Ready to run", b);
            // Note that the test is cheating here slightly.
            // The watch in Reaper is still running across the in-JVM restarts,
            // whereas in production it would have been cancelled during the shutdown.
            // But it does not matter since we are waiting for the agent to come back online after the restart,
            // which is sufficient trigger to reactivate the reaper.
            // Indeed we get two Reaper instances running, which independently remove the node.
            deletePods(cloud.connect(), getLabels(this, name), false);
            r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
            r.waitForMessage(new ExecutorStepExecution.RemovedNodeCause().getShortDescription(), b);
        });
    }

    @Test
    public void getContainerLogWithRestart() throws Exception {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            r.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1",
                    Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP,
                    Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            // we need to wait until we are sure that the sh
            // step has started...
            r.waitForMessage("+ sleep 5", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins.getItemByFullName(projectName.get(), WorkflowJob.class).getBuildByNumber(1);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            r.assertLogContains("[Pipeline] containerLog", b);
            r.assertLogContains("[Pipeline] End of Pipeline", b);
        });
    }

    private WorkflowRun getPipelineJobThenScheduleRun(JenkinsRule r) throws InterruptedException, ExecutionException, IOException {
        return createPipelineJobThenScheduleRun(r, getClass(), name.getMethodName());
    }
}
