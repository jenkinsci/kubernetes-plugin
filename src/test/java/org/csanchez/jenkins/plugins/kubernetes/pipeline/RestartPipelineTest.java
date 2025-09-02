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
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Node;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Reaper;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepDynamicContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class RestartPipelineTest {

    private static final String CONTAINER_ENV_VAR_VALUE = "container-env-var-value";
    private static final String POD_ENV_VAR_VALUE = "pod-env-var-value";
    private static final String SECRET_KEY = "password";

    private KubernetesCloud cloud;

    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();

    @TempDir
    private File tmp;

    @SuppressWarnings("unused")
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    private final LogRecorder logs = new LogRecorder()
            .record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()), Level.ALL);
    // .record("org.jenkinsci.plugins.durabletask",
    // Level.ALL).record("org.jenkinsci.plugins.workflow.support.concurrent",
    // Level.ALL).record("org.csanchez.jenkins.plugins.kubernetes.pipeline", Level.ALL);

    private String name;

    @BeforeAll
    static void beforeAll() {
        assumeKubernetes();
    }

    @BeforeEach
    void beforeEach(TestInfo info) {
        name = info.getTestMethod().orElseThrow().getName();
    }

    private static void setEnvVariables(PodTemplate podTemplate) {
        TemplateEnvVar podSecretEnvVar = new SecretEnvVar("POD_ENV_VAR_FROM_SECRET", "pod-secret", SECRET_KEY, false);
        TemplateEnvVar podSimpleEnvVar = new KeyValueEnvVar("POD_ENV_VAR", POD_ENV_VAR_VALUE);
        podTemplate.setEnvVars(asList(podSecretEnvVar, podSimpleEnvVar));
        TemplateEnvVar containerEnvVariable = new KeyValueEnvVar("CONTAINER_ENV_VAR", CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerEnvVariableLegacy =
                new ContainerEnvVar("CONTAINER_ENV_VAR_LEGACY", CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerSecretEnvVariable =
                new SecretEnvVar("CONTAINER_ENV_VAR_FROM_SECRET", "container-secret", SECRET_KEY, false);
        podTemplate
                .getContainers()
                .get(0)
                .setEnvVars(asList(containerEnvVariable, containerEnvVariableLegacy, containerSecretEnvVariable));
    }

    private PodTemplate buildBusyboxTemplate(String label) {
        // Create a busybox template
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(label);
        podTemplate.setTerminationGracePeriodSeconds(0L);

        ContainerTemplate containerTemplate = new ContainerTemplate("busybox", "busybox", "cat", "");
        containerTemplate.setTtyEnabled(true);
        podTemplate.getContainers().add(containerTemplate);
        setEnvVariables(podTemplate);
        return podTemplate;
    }

    private void configureCloud() throws Exception {
        cloud = setupCloud(this, name);
        createSecret(cloud.connect(), cloud.getNamespace());
        cloud.getTemplates().clear();
        cloud.addTemplate(buildBusyboxTemplate("busybox"));

        setupHost(cloud);

        Jenkins.get().clouds.add(cloud);
    }

    private void configureAgentListener() throws Exception {
        // Take random port and fix it, to be the same after Jenkins restart
        int fixedPort = Jenkins.get().getTcpSlaveAgentListener().getAdvertisedPort();
        Jenkins.get().setSlaveAgentPort(fixedPort);
    }

    @Test
    void nullLabelSupportsRestart() throws Throwable {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            PodTemplate pt = new PodTemplate();
            pt.setName("test");
            pt.setNodeUsageMode(Node.Mode.NORMAL);
            ContainerTemplate ct = new ContainerTemplate("busybox", "busybox");
            ct.setTtyEnabled(true);
            ct.setCommand("/bin/cat");
            pt.setContainers(Collections.singletonList(ct));
            cloud.setTemplates(Collections.singletonList(pt));
            r.jenkins.setNumExecutors(0);
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            r.waitForMessage("+ sleep 5", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            r.waitForMessage("Ready to run", b);
            r.assertLogContains("finished the test!", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Test
    void runInPodWithRestartWithMultipleContainerCalls() throws Throwable {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            r.jenkins.addNode(new DumbSlave("slave", newFolder(tmp, "remoteFS").getPath(), new JNLPLauncher(false)));
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            // we need to wait until we are sure that the sh
            // step has started...
            r.waitForMessage("+ sleep 5", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            r.assertLogContains("finished the test!", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Test
    void runInPodWithRestartWithLongSleep() throws Throwable {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            r.jenkins.addNode(new DumbSlave("slave", newFolder(tmp, "remoteFS").getPath(), new JNLPLauncher(false)));
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            // we need to wait until we are sure that the sh
            // step has started...
            r.waitForMessage("+ sleep 5", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            r.assertLogContains("finished the test!", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Test
    void windowsRestart() throws Throwable {
        assumeWindows(WINDOWS_1809_BUILD);
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
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            r.assertLogContains("sleeping #9", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
            r.assertLogContains("finished the test!", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-49707")
    @Test
    void terminatedPodAfterRestart() throws Throwable {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            r.waitForMessage("+ sleep", b);
        });
        logs.record(DurableTaskStep.class, Level.FINE)
                .record(Reaper.class, Level.FINE)
                .record(ExecutorStepDynamicContext.class, Level.FINE);
        story.then(r -> {
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            r.waitForMessage("Ready to run", b);
            deletePods(cloud.connect(), getLabels(this, name), false);
            r.waitForMessage("Agent was removed", b);
            r.waitForMessage("Retrying", b);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
        });
    }

    @Test
    void taskListenerAfterRestart() throws Throwable {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            r.waitForMessage("+ sleep", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            Optional<Node> first = r.jenkins.getNodes().stream()
                    .filter(KubernetesSlave.class::isInstance)
                    .findFirst();
            assertTrue(first.isPresent(), "Kubernetes node should be present after restart");
            KubernetesSlave node = (KubernetesSlave) first.get();
            r.waitForMessage("Ready to run", b);
            waitForTemplate(node).getListener().getLogger().println("This got printed");
            r.waitForMessage("This got printed", b);
            b.getExecutor().interrupt();
            r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        });
    }

    @Test
    void taskListenerAfterRestart_multipleLabels() throws Throwable {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            r.waitForMessage("+ sleep", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            Optional<Node> first = r.jenkins.getNodes().stream()
                    .filter(KubernetesSlave.class::isInstance)
                    .findFirst();
            assertTrue(first.isPresent(), "Kubernetes node should be present after restart");
            KubernetesSlave node = (KubernetesSlave) first.get();
            r.waitForMessage("Ready to run", b);
            waitForTemplate(node).getListener().getLogger().println("This got printed");
            r.waitForMessage("This got printed", b);
            b.getExecutor().interrupt();
            r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        });
    }

    private PodTemplate waitForTemplate(KubernetesSlave node) throws Exception {
        while (node.getTemplateOrNull() == null) {
            Thread.sleep(100L);
        }
        return node.getTemplate();
    }

    @Test
    void getContainerLogWithRestart() throws Throwable {
        AtomicReference<String> projectName = new AtomicReference<>();
        story.then(r -> {
            configureAgentListener();
            configureCloud();
            r.jenkins.addNode(new DumbSlave("slave", newFolder(tmp, "remoteFS").getPath(), new JNLPLauncher(false)));
            WorkflowRun b = getPipelineJobThenScheduleRun(r);
            projectName.set(b.getParent().getFullName());
            // we need to wait until we are sure that the sh
            // step has started...
            r.waitForMessage("+ sleep 5", b);
        });
        story.then(r -> {
            WorkflowRun b = r.jenkins
                    .getItemByFullName(projectName.get(), WorkflowJob.class)
                    .getBuildByNumber(1);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            r.assertLogContains("[Pipeline] containerLog", b);
            r.assertLogContains("[Pipeline] End of Pipeline", b);
        });
    }

    private WorkflowRun getPipelineJobThenScheduleRun(JenkinsRule r) throws Exception {
        return createPipelineJobThenScheduleRun(r, getClass(), name);
    }

    private static File newFolder(File root, String... subDirs) throws Exception {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
