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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.Issue;

import com.google.common.collect.ImmutableMap;

import hudson.Launcher;
import hudson.Launcher.DummyLauncher;
import hudson.Launcher.ProcStarter;
import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * @author Carlos Sanchez
 */
public class ContainerExecDecoratorTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private static KubernetesClient client;
    private static final Pattern PID_PATTERN = Pattern.compile("^(pid is \\d+)$", Pattern.MULTILINE);

    private ContainerExecDecorator decorator;
    private Pod pod;

    @BeforeClass
    public static void isKubernetesConfigured() throws Exception {
        assumeKubernetes();
    }

    @Before
    public void configureCloud() throws Exception {
        client = setupCloud(this).connect();
        deletePods(client, getLabels(this), false);

        String image = "busybox";
        Container c = new ContainerBuilder().withName(image).withImagePullPolicy("IfNotPresent").withImage(image)
                .withCommand("cat").withTty(true).build();
        String podName = "test-command-execution-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        pod = client.pods().create(new PodBuilder().withNewMetadata().withName(podName).withLabels(getLabels(this))
                .endMetadata().withNewSpec().withContainers(c).endSpec().build());

        System.out.println("Created pod: " + pod.getMetadata().getName());

        decorator = new ContainerExecDecorator(client, pod.getMetadata().getName(), image, client.getNamespace());
    }

    @After
    public void after() throws Exception {
        deletePods(client, getLabels(this), false);
    }

    @Test(timeout = 10000)
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
            assertTrue("Output should contain pid: " + r.output, PID_PATTERN.matcher(r.output).find());
            assertEquals(0, r.exitCode);
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
            r = execCommand(false, "sh", "-c", "cd /tmp; echo pid is $$$$ > test; cat /tmp/test");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        results.add(r);
    }

    @Test
    public void testCommandExecutionFailure() throws Exception {
        ProcReturn r = execCommand(false, "false");
        assertEquals(1, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testCommandExecutionFailureHighError() throws Exception {
        ProcReturn r = execCommand(false, "sh", "-c", "return 127");
        assertEquals(127, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testQuietCommandExecution() throws Exception {
        ProcReturn r = execCommand(true, "echo", "pid is 9999");
        assertFalse("Output should not contain command: " + r.output, PID_PATTERN.matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testCommandExecutionWithNohup() throws Exception {
        ProcReturn r = execCommand(false, "nohup", "sh", "-c", "sleep 5; cd /tmp; echo pid is $$$$ > test; cat /tmp/test");
        assertTrue("Output should contain pid: " + r.output, PID_PATTERN.matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void commandsEscaping() {
        ProcStarter procStarter = new DummyLauncher(null).launch();
        procStarter = procStarter.cmds("$$$$", "$$?");
        String[] commands = ContainerExecDecorator.getCommands(procStarter);
        assertArrayEquals(new String[] { "\\$\\$", "\\$?" }, commands);
    }

    @Test
    public void testCommandExecutionWithEscaping() throws Exception {
        ProcReturn r = execCommand(false, "sh", "-c", "cd /tmp; false; echo result is $$? > test; cat /tmp/test");
        assertTrue("Output should contain result: " + r.output,
                Pattern.compile("^(result is 1)$", Pattern.MULTILINE).matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testCommandExecutionWithNohupAndError() throws Exception {
        ProcReturn r = execCommand(false, "nohup", "sh", "-c", "sleep 5; return 127");
        assertEquals(127, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @Issue("JENKINS-46719")
    public void testContainerDoesNotExist() throws Exception {
        decorator = new ContainerExecDecorator(client, pod.getMetadata().getName(), "doesNotExist", client.getNamespace());
        exception.expect(IOException.class);
        exception.expectMessage(containsString("container [doesNotExist] does not exist in pod ["));
        execCommand(false, "nohup", "sh", "-c", "sleep 5; return 127");
    }

    private ProcReturn execCommand(boolean quiet, String... cmd) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Launcher launcher = decorator
                .decorate(new DummyLauncher(new StreamTaskListener(new TeeOutputStream(out, System.out))), null);
        ContainerExecProc proc = (ContainerExecProc) launcher
                .launch(launcher.new ProcStarter().pwd("/tmp").cmds(cmd).quiet(quiet));
        assertTrue(proc.isAlive());
        int exitCode = proc.joinWithTimeout(10, TimeUnit.SECONDS, StreamTaskListener.fromStderr());
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
