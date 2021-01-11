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
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.AllContainersRunningPodWatcher;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesClientProvider;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;

import hudson.Launcher;
import hudson.Launcher.DummyLauncher;
import hudson.Launcher.ProcStarter;
import hudson.model.Node;
import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.OkHttpClient;
import org.junit.Ignore;

/**
 * @author Carlos Sanchez
 */
public class ContainerExecDecoratorTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private KubernetesCloud cloud;
    private static KubernetesClient client;
    private static final Pattern PID_PATTERN = Pattern.compile("^((?:\\[\\d+\\] )?pid is \\d+)$", Pattern.MULTILINE);

    private ContainerExecDecorator decorator;
    private Pod pod;
    private KubernetesSlave agent;

    @Rule
    public Timeout timeout = new Timeout(3, TimeUnit.MINUTES);

    @Rule
    public LoggerRule containerExecLogs = new LoggerRule()
            .record(Logger.getLogger(ContainerExecDecorator.class.getName()), Level.ALL) //
            .record(Logger.getLogger(KubernetesClientProvider.class.getName()), Level.ALL);

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void isKubernetesConfigured() throws Exception {
        assumeKubernetes();
    }

    @Before
    public void configureCloud() throws Exception {
        cloud = setupCloud(this, name);
        client = cloud.connect();
        deletePods(client, getLabels(this, name), false);

        String image = "busybox";
        String podName = "test-command-execution-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        pod = client.pods().create(new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withLabels(getLabels(this, name))
                .endMetadata()
                .withNewSpec()
                    .withContainers(new ContainerBuilder()
                                .withName(image)
                                .withImagePullPolicy("IfNotPresent")
                                .withImage(image)
                                .withCommand("cat")
                                .withTty(true)
                            .build(), new ContainerBuilder()
                            .withName(image + "1")
                            .withImagePullPolicy("IfNotPresent")
                            .withImage(image)
                            .withCommand("cat")
                            .withTty(true)
                            .withWorkingDir("/home/jenkins/agent1")
                            .build())
                    .withNodeSelector(Collections.singletonMap("kubernetes.io/os", "linux"))
                    .withTerminationGracePeriodSeconds(0L)
                .endSpec().build());

        System.out.println("Created pod: " + pod.getMetadata().getName());
        AllContainersRunningPodWatcher watcher = new AllContainersRunningPodWatcher(client, pod, TaskListener.NULL);
        try (Watch w1 = client.pods().withName(podName).watch(watcher);) {
            assert watcher != null; // assigned 3 lines above
            watcher.await(30, TimeUnit.SECONDS);
        }
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

    @After
    public void after() throws Exception {
        client.pods().delete(pod);
        deletePods(client, getLabels(this, name), true);
    }

    /**
     * Test that multiple command execution in parallel works.
     */
    @Ignore("TODO PID_PATTERN match flaky in CI")
    @Test
    public void testCommandExecution() throws Exception {
        Thread[] t = new Thread[10];
        List<ProcReturn> results = Collections.synchronizedList(new ArrayList<>(t.length));
        for (int i = 0; i < t.length; i++) {
            t[i] = newThread(i, results);
        }
        for (int i = 0; i < t.length; i++) {
            t[i].start();
        }
        for (int i = 0; i < t.length; i++) {
            t[i].join();
        }
        assertEquals("Not all threads finished successfully", t.length, results.size());
        for (ProcReturn r : results) {
            assertEquals("Command didn't complete in time or failed", 0, r.exitCode);
            assertTrue("Output should contain pid: " + r.output, PID_PATTERN.matcher(r.output).find());
            assertFalse(r.proc.isAlive());
        }
    }

    private Thread newThread(int i, List<ProcReturn> results) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    command(results, i);
                } finally {
                    System.out.println("Thread " + i + " finished");
                }
            }
        }, "test-" + i);
    }

    private void command(List<ProcReturn> results, int i) {
        ProcReturn r;
        try {
            r = execCommand(false, false, "sh", "-c", "cd /tmp; echo ["+i+"] pid is $$$$ > test; cat /tmp/test");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        results.add(r);
    }

    @Test
    public void testCommandExecutionFailure() throws Exception {
        ProcReturn r = execCommand(false, false, "false");
        assertEquals(1, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testCommandExecutionFailureHighError() throws Exception {
        ProcReturn r = execCommand(false, false, "sh", "-c", "return 127");
        assertEquals(127, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testQuietCommandExecution() throws Exception {
        ProcReturn r = execCommand(true, false, "echo", "pid is 9999");
        assertFalse("Output should not contain command: " + r.output, PID_PATTERN.matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testCommandExecutionWithNohup() throws Exception {
        ProcReturn r = execCommand(false, false, "nohup", "sh", "-c",
                "sleep 5; cd /tmp; echo pid is $$$$ > test; cat /tmp/test");
        assertTrue("Output should contain pid: " + r.output, PID_PATTERN.matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void commandsEscaping() {
        ProcStarter procStarter = new DummyLauncher(null).launch();
        procStarter = procStarter.cmds("$$$$", "$$?");

        String[] commands = ContainerExecDecorator.getCommands(procStarter, null);
        assertArrayEquals(new String[] { "\\$\\$", "\\$?" }, commands);
    }

    @Test
    public void testCommandExecutionWithEscaping() throws Exception {
        ProcReturn r = execCommand(false, false, "sh", "-c", "cd /tmp; false; echo result is $$? > test; cat /tmp/test");
        assertTrue("Output should contain result: " + r.output,
                Pattern.compile("^(result is 1)$", Pattern.MULTILINE).matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @Issue("JENKINS-62502")
    public void testCommandExecutionEscapingDoubleQuotes() throws Exception {
        ProcReturn r = execCommand(false, false, "sh", "-c", "cd /tmp; false; echo \"result is 1\" > test; cat /tmp/test");
        assertTrue("Output should contain result: " + r.output,
                Pattern.compile("^(result is 1)$", Pattern.MULTILINE).matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }
	
	@Test
	public void testCommandExecutionOutput() throws Exception {
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
    public void testCommandExecutionWithNohupAndError() throws Exception {
        ProcReturn r = execCommand(false, false, "nohup", "sh", "-c", "sleep 5; return 127");
        assertEquals(127, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @Issue("JENKINS-46719")
    public void testContainerDoesNotExist() throws Exception {
        decorator.setContainerName("doesNotExist");
        exception.expect(KubernetesClientException.class);
        exception.expectMessage(containsString("container doesNotExist is not valid for pod"));
        execCommand(false, false, "nohup", "sh", "-c", "sleep 5; return 127");
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
    public void testRejectedExecutionException() throws Exception {
        assertTrue(client instanceof HttpClientAware);
        OkHttpClient httpClient = ((HttpClientAware) client).getHttpClient();
        System.out.println("Max requests: " + httpClient.dispatcher().getMaxRequests() + "/"
                + httpClient.dispatcher().getMaxRequestsPerHost());
        System.out.println("Connection count: " + httpClient.connectionPool().connectionCount() + " - "
                + httpClient.connectionPool().idleConnectionCount());
        List<Thread> threads = new ArrayList<>();
        final AtomicInteger errors = new AtomicInteger(0);
        for (int i = 0; i < 10; i++) {
            final String name = "Thread " + i;
            Thread t = new Thread(() -> {
                try {
                    System.out.println(name + " Connection count: " + httpClient.connectionPool().connectionCount()
                            + " - " + httpClient.connectionPool().idleConnectionCount());
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
        threads.stream().forEach(t -> t.start());
        threads.stream().forEach(t -> {
            try {
                System.out.println("Waiting for " + t.getName());
                t.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("Connection count: " + httpClient.connectionPool().connectionCount() + " - "
                + httpClient.connectionPool().idleConnectionCount());
        assertEquals("Errors in threads", 0, errors.get());
    }

    @Test
    @Issue("JENKINS-50429")
    public void testContainerExecPerformance() throws Exception {
        for (int i = 0; i < 10; i++) {
            ProcReturn r = execCommand(false, false, "ls");
        }
    }

    @Test
    @Issue("JENKINS-58975")
    public void testContainerExecOnCustomWorkingDir() throws Exception {
        doReturn(null).when((Node)agent).toComputer();
        ProcReturn r = execCommandInContainer("busybox1", agent, false, false, "env");
        assertTrue("Environment variable workingDir1 should be changed to /home/jenkins/agent1",
                r.output.contains("workingDir1=/home/jenkins/agent1"));
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @Issue("JENKINS-58975")
    public void testContainerExecOnCustomWorkingDirWithComputeEnvVars() throws Exception {
        EnvVars computeEnvVars = new EnvVars();
        computeEnvVars.put("MyDir", "dir");
        computeEnvVars.put("MyCustomDir", "/home/jenkins/agent");
        Computer computer = mock(Computer.class);
        doReturn(computeEnvVars).when(computer).getEnvironment();

        doReturn(computer).when((Node)agent).toComputer();
        ProcReturn r = execCommandInContainer("busybox1", agent, false, false, "env");
        assertTrue("Environment variable workingDir1 should be changed to /home/jenkins/agent1",
                r.output.contains("workingDir1=/home/jenkins/agent1"));
        assertTrue("Environment variable MyCustomDir should be changed to /home/jenkins/agent1",
                r.output.contains("MyCustomDir=/home/jenkins/agent1"));
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    private ProcReturn execCommand(boolean quiet, boolean launcherStdout, String... cmd) throws Exception {
        return execCommandInContainer(null, null, quiet, launcherStdout, cmd);
    }

    private ProcReturn execCommandInContainer(String containerName, Node node, boolean quiet, boolean launcherStdout, String... cmd) throws Exception {
        if (containerName != null && !containerName.isEmpty()) {
            decorator.setContainerName(containerName);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DummyLauncher dummyLauncher = new DummyLauncher(new StreamTaskListener(new TeeOutputStream(out, System.out)));
        Launcher launcher = decorator.decorate(dummyLauncher, node);
        Map<String, String> envs = new HashMap<>(100);
        for (int i = 0; i < 50; i++) {
            envs.put("aaaaaaaa" + i, "bbbbbbbb");
        }
        envs.put("workingDir1", "/home/jenkins/agent");

        ProcStarter procStarter = launcher.new ProcStarter().pwd("/tmp").cmds(cmd).envs(envs).quiet(quiet);
        if (launcherStdout) {
            procStarter.stdout(dummyLauncher.getListener());
        }
        ContainerExecProc proc = (ContainerExecProc) launcher.launch(procStarter);
        // wait for proc to finish (shouldn't take long)
        for (int i = 0; proc.isAlive() && i < 200; i++) {
            Thread.sleep(100);
        }
        assertFalse("proc is alive", proc.isAlive());
        int exitCode = proc.join();
        return new ProcReturn(proc, exitCode, out.toString());
    }

    class ProcReturn {
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
