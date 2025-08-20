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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assumeKubernetes;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.setupCloud;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.Launcher.DummyLauncher;
import hudson.Launcher.ProcStarter;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesClientProvider;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Carlos Sanchez
 */
@WithJenkins
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ContainerExecDecoratorTest {

    private JenkinsRule j;

    private KubernetesCloud cloud;
    private static KubernetesClient client;
    private static final Pattern PID_PATTERN = Pattern.compile("^((?:\\[\\d+\\] )?pid is \\d+)$", Pattern.MULTILINE);

    private ContainerExecDecorator decorator;
    private Pod pod;
    private KubernetesSlave agent;
    private DumbSlave dumbAgent;

    @SuppressWarnings("unused")
    private final LogRecorder containerExecLogs = new LogRecorder()
            .record(Logger.getLogger(ContainerExecDecorator.class.getName()), Level.ALL) //
            .record(ContainerExecProc.class, Level.ALL) //
            .record(Logger.getLogger(KubernetesClientProvider.class.getName()), Level.ALL);

    private String name;

    @BeforeAll
    static void beforeAll() {
        assumeKubernetes();
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule, TestInfo info) throws Exception {
        j = rule;
        name = info.getTestMethod().orElseThrow().getName();
        cloud = setupCloud(this, name);
        client = cloud.connect();
        deletePods(client, getLabels(this, name), false);

        String image = "busybox";
        String podName = "test-command-execution-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        pod = client.pods()
                .create(new PodBuilder()
                        .withNewMetadata()
                        .withName(podName)
                        .withLabels(getLabels(this, name))
                        .endMetadata()
                        .withNewSpec()
                        .withContainers(
                                new ContainerBuilder()
                                        .withName(image)
                                        .withImagePullPolicy("IfNotPresent")
                                        .withImage(image)
                                        .withCommand("cat")
                                        .withTty(true)
                                        .build(),
                                new ContainerBuilder()
                                        .withName(image + "1")
                                        .withImagePullPolicy("IfNotPresent")
                                        .withImage(image)
                                        .withCommand("cat")
                                        .withTty(true)
                                        .withWorkingDir("/home/jenkins/agent1")
                                        .build())
                        .withNodeSelector(Collections.singletonMap("kubernetes.io/os", "linux"))
                        .withTerminationGracePeriodSeconds(0L)
                        .endSpec()
                        .build());

        System.out.println("Created pod: " + pod.getMetadata().getName());
        client.pods().withName(podName).waitUntilReady(30, TimeUnit.SECONDS);
        PodTemplate template = new PodTemplate();
        template.setName(pod.getMetadata().getName());
        agent = mock(KubernetesSlave.class);
        when(agent.getNamespace()).thenReturn(client.getNamespace());
        when(agent.getPodName()).thenReturn(pod.getMetadata().getName());
        doReturn(cloud).when(agent).getKubernetesCloud();
        when(agent.getPod()).thenReturn(Optional.of(pod));
        StepContext context = mock(StepContext.class);
        when(context.get(Node.class)).thenReturn(agent);

        decorator = new ContainerExecDecorator();
        decorator.setNodeContext(new KubernetesNodeContext(context));
        decorator.setContainerName(image);
    }

    @AfterEach
    void afterEach() throws Exception {
        client.pods().delete(pod);
        deletePods(client, getLabels(this, name), true);
    }

    /**
     * Test that multiple command execution in parallel works.
     */
    @Disabled("TODO PID_PATTERN match flaky in CI")
    @Test
    void testCommandExecution() throws Exception {
        Thread[] t = new Thread[10];
        List<ProcReturn> results = Collections.synchronizedList(new ArrayList<>(t.length));
        for (int i = 0; i < t.length; i++) {
            t[i] = newThread(i, results);
        }
        for (Thread thread : t) {
            thread.start();
        }
        for (Thread thread : t) {
            thread.join();
        }
        assertEquals(t.length, results.size(), "Not all threads finished successfully");
        for (ProcReturn r : results) {
            assertEquals(0, r.exitCode, "Command didn't complete in time or failed");
            assertTrue(PID_PATTERN.matcher(r.output).find(), "Output should contain pid: " + r.output);
            assertFalse(r.proc.isAlive());
        }
    }

    private Thread newThread(int i, List<ProcReturn> results) {
        return new Thread(
                () -> {
                    try {
                        command(results, i);
                    } finally {
                        System.out.println("Thread " + i + " finished");
                    }
                },
                "test-" + i);
    }

    private void command(List<ProcReturn> results, int i) {
        ProcReturn r;
        try {
            r = execCommand(false, false, "sh", "-c", "cd /tmp; echo [" + i + "] pid is $$$$ > test; cat /tmp/test");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        results.add(r);
    }

    @Test
    void testCommandExecutionFailure() throws Exception {
        ProcReturn r = execCommand(false, false, "false");
        assertEquals(1, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    void testCommandExecutionFailureHighError() throws Exception {
        ProcReturn r = execCommand(false, false, "sh", "-c", "return 127");
        assertEquals(127, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    void testQuietCommandExecution() throws Exception {
        ProcReturn r = execCommand(true, false, "echo", "pid is 9999");
        assertFalse(PID_PATTERN.matcher(r.output).find(), "Output should not contain command: " + r.output);
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    void testCommandExecutionWithNohup() throws Exception {
        ProcReturn r = execCommand(
                false, false, "nohup", "sh", "-c", "sleep 5; cd /tmp; echo pid is $$$$ > test; cat /tmp/test");
        assertTrue(PID_PATTERN.matcher(r.output).find(), "Output should contain pid: " + r.output);
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    void commandsEscaping() {
        DummyLauncher launcher = new DummyLauncher(null);
        assertThat(
                ContainerExecDecorator.getCommands(launcher.launch().cmds("$$$$", "$$?"), null, true),
                arrayContaining("\\$\\$", "\\$?"));
        assertThat(
                ContainerExecDecorator.getCommands(launcher.launch().cmds("\""), null, true), arrayContaining("\\\""));
        assertThat(
                ContainerExecDecorator.getCommands(launcher.launch().cmds("\"\""), null, false),
                arrayContaining("\"\""));
    }

    @Test
    void testCommandExecutionWithEscaping() throws Exception {
        ProcReturn r =
                execCommand(false, false, "sh", "-c", "cd /tmp; false; echo result is $$? > test; cat /tmp/test");
        assertTrue(
                Pattern.compile("^(result is 1)$", Pattern.MULTILINE)
                        .matcher(r.output)
                        .find(),
                "Output should contain result: " + r.output);
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @Issue("JENKINS-62502")
    void testCommandExecutionEscapingDoubleQuotes() throws Exception {
        ProcReturn r =
                execCommand(false, false, "sh", "-c", "cd /tmp; false; echo \"result is 1\" > test; cat /tmp/test");
        assertTrue(
                Pattern.compile("^(result is 1)$", Pattern.MULTILINE)
                        .matcher(r.output)
                        .find(),
                "Output should contain result: " + r.output);
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    void testCommandExecutionOutput() throws Exception {
        String testString = "Should appear once";

        // Check output with quiet=false
        ProcReturn r = execCommand(false, false, "sh", "-c", "echo " + testString);
        assertEquals(0, r.exitCode);
        String output = StringUtils.substringAfter(r.output, "exit");
        assertEquals(testString, output.trim());

        // Check output with quiet=false and outputForCaller same as launcher output
        r = execCommand(false, true, "sh", "-c", "echo " + testString);
        assertEquals(0, r.exitCode);
        output = StringUtils.substringAfter(r.output, "exit");
        assertEquals(testString, output.trim());

        // Check output with quiet=true
        r = execCommand(true, false, "sh", "-c", "echo " + testString);
        assertEquals(0, r.exitCode);
        assertEquals("", r.output.trim());

        // Check output with quiet=true and outputForCaller same as launcher output
        r = execCommand(true, true, "sh", "-c", "echo " + testString);
        assertEquals(0, r.exitCode);
        assertEquals(testString, r.output.trim());
    }

    @Test
    void testCommandExecutionWithNohupAndError() throws Exception {
        ProcReturn r = execCommand(false, false, "nohup", "sh", "-c", "sleep 5; return 127");
        assertEquals(127, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @Issue("JENKINS-46719")
    void testContainerDoesNotExist() {
        decorator.setContainerName("doesNotExist");
        Throwable exception = assertThrows(
                KubernetesClientException.class,
                () -> execCommand(false, false, "nohup", "sh", "-c", "sleep 5; return 127"));
        assertThat(exception.getMessage(), containsString("container doesNotExist not found in pod"));
    }

    /**
     * Reproduce JENKINS-55392
     *
     * Caused by: java.util.concurrent.RejectedExecutionException: Task okhttp3.RealCall$AsyncCall@30f55c9f rejected
     * from java.util.concurrent.ThreadPoolExecutor@25634758[Terminated, pool size = 0, active threads = 0, queued tasks
     * = 0, completed tasks = 0]
     *
     * @throws Exception
     */
    @Test
    @Issue("JENKINS-55392")
    void testRejectedExecutionException() throws Exception {
        List<Thread> threads = new ArrayList<>();
        final AtomicInteger errors = new AtomicInteger(0);
        for (int i = 0; i < 10; i++) {
            final String name = "Thread " + i;
            Thread t = new Thread(() -> {
                try {
                    ProcReturn r = execCommand(false, false, "echo", "test");
                } catch (Exception e) {
                    errors.incrementAndGet();
                    System.out.println(e.getMessage());
                }
            });
            threads.add(t);
        }

        // force expiration of client
        KubernetesClientProvider.invalidate(cloud.getDisplayName());
        cloud.connect();
        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                System.out.println("Waiting for " + t.getName());
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        assertEquals(0, errors.get(), "Errors in threads");
    }

    @Test
    @Issue("JENKINS-50429")
    void testContainerExecPerformance() throws Exception {
        for (int i = 0; i < 10; i++) {
            ProcReturn r = execCommand(false, false, "ls");
        }
    }

    @Test
    @Issue("JENKINS-58975")
    void testContainerExecOnCustomWorkingDir() throws Exception {
        doReturn(null).when((Node) agent).toComputer();
        ProcReturn r = execCommandInContainer("busybox1", agent, false, false, "env");
        assertTrue(
                r.output.contains("workingDir1=/home/jenkins/agent1"),
                "Environment variable workingDir1 should be changed to /home/jenkins/agent1");
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @Issue("JENKINS-58975")
    void testContainerExecOnCustomWorkingDirWithComputeEnvVars() throws Exception {
        EnvVars computeEnvVars = new EnvVars();
        computeEnvVars.put("MyDir", "dir");
        computeEnvVars.put("MyCustomDir", "/home/jenkins/agent");
        Computer computer = mock(Computer.class);
        doReturn(computeEnvVars).when(computer).getEnvironment();

        doReturn(computer).when((Node) agent).toComputer();
        ProcReturn r = execCommandInContainer("busybox1", agent, false, false, "env");
        assertTrue(
                r.output.contains("workingDir1=/home/jenkins/agent1"),
                "Environment variable workingDir1 should be changed to /home/jenkins/agent1");
        assertTrue(
                r.output.contains("MyCustomDir=/home/jenkins/agent1"),
                "Environment variable MyCustomDir should be changed to /home/jenkins/agent1");
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    /**
     * Reproduce JENKINS-66986
     *
     * Allow non KubernetesSlave nodes to be provisioned inside the container clause
     *
     * @throws Exception
     */
    @Test
    @Issue("JENKINS-66986")
    void testRunningANonKubernetesNodeInsideContainerClause() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DummyLauncher dummyLauncher =
                new DummyLauncher(new StreamTaskListener(new TeeOutputStream(out, System.out), StandardCharsets.UTF_8));
        dumbAgent = j.createSlave("test", "", null);
        Launcher launcher = decorator.decorate(dummyLauncher, dumbAgent);
        assertEquals(dummyLauncher, launcher);
    }

    private ProcReturn execCommand(boolean quiet, boolean launcherStdout, String... cmd) throws Exception {
        return execCommandInContainer(null, null, quiet, launcherStdout, cmd);
    }

    private ProcReturn execCommandInContainer(
            String containerName, Node node, boolean quiet, boolean launcherStdout, String... cmd) throws Exception {
        if (containerName != null && !containerName.isEmpty()) {
            decorator.setContainerName(containerName);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DummyLauncher dummyLauncher =
                new DummyLauncher(new StreamTaskListener(new TeeOutputStream(out, System.out), StandardCharsets.UTF_8));
        Launcher launcher = decorator.decorate(dummyLauncher, node);
        Map<String, String> envs = new HashMap<>(100);
        for (int i = 0; i < 50; i++) {
            envs.put("aaaaaaaa" + i, "bbbbbbbb");
        }
        envs.put("workingDir1", "/home/jenkins/agent");

        ProcStarter procStarter =
                launcher.new ProcStarter().pwd("/tmp").cmds(cmd).envs(envs).quiet(quiet);
        if (launcherStdout) {
            procStarter.stdout(dummyLauncher.getListener());
        }
        ContainerExecProc proc = (ContainerExecProc) launcher.launch(procStarter);
        // wait for proc to finish (shouldn't take long)
        for (int i = 0; proc.isAlive() && i < 200; i++) {
            Thread.sleep(100);
        }
        assertFalse(proc.isAlive(), "proc is alive");
        int exitCode = proc.join();
        return new ProcReturn(proc, exitCode, out.toString());
    }

    static class ProcReturn {
        public int exitCode;
        public String output;
        public ContainerExecProc proc;

        ProcReturn(ContainerExecProc proc, int exitCode, String output) {
            this.proc = proc;
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
