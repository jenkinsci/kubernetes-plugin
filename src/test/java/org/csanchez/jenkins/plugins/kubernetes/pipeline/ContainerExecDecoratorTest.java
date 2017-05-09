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

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import hudson.Launcher;
import hudson.Launcher.DummyLauncher;
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

    private static KubernetesClient client;
    private static Map<String, String> labels = Collections.singletonMap("class",
            ContainerExecDecoratorTest.class.getSimpleName());
    private static final Pattern PID_PATTERN = Pattern.compile("^(pid is \\d+)$", Pattern.MULTILINE);

    @BeforeClass
    public static void configureCloud() throws Exception {
        // do not run if minikube is not running
        assumeMiniKube();
        client = setupCloud().connect();
        deletePods();
    }

    @AfterClass
    public static void deletePods() throws Exception {
        if (client != null) {
            client.pods().withLabel("class", labels.get("class")).delete();
        }
    }

    @Test
    public void testCommandExecution() throws Exception {
        ProcReturn r = execCommand(false, "sh", "-c", "cd /tmp; echo pid is $$$$ > test; cat /tmp/test");
        assertTrue("Output should contain pid: " + r.output, PID_PATTERN.matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
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
        ProcReturn r = execCommand(false, "nohup", "sh", "-c", "cd /tmp; echo pid is $$$$ > test; cat /tmp/test");
        assertFalse("Output should not contain pid: " + r.output, PID_PATTERN.matcher(r.output).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    public void testCommandExecutionWithNohupAndError() throws Exception {
        ProcReturn r = execCommand(false, "nohup", "sh", "-c", "sleep 5; return 127");
        assertFalse("Output should not contain pid: " + r.output, PID_PATTERN.matcher(r.output).find());
        assertEquals(127, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    private ProcReturn execCommand(boolean quiet, String... cmd) throws Exception {
        String image = "busybox";
        Container c = new ContainerBuilder().withName(image).withImagePullPolicy("IfNotPresent").withImage(image)
                .withCommand("cat").withTty(true).build();
        String podName = "test-command-execution-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        Pod pod = client.pods().create(new PodBuilder().withNewMetadata().withName(podName).withLabels(labels)
                .endMetadata().withNewSpec().withContainers(c).endSpec().build());

        System.out.println("Created pod: " + pod.getMetadata().getName());

        final AtomicBoolean podAlive = new AtomicBoolean(false);
        final CountDownLatch podStarted = new CountDownLatch(1);
        final CountDownLatch podFinished = new CountDownLatch(1);

        try (ContainerExecDecorator decorator = new ContainerExecDecorator(client, pod.getMetadata().getName(), image,
                podAlive, podStarted, podFinished, client.getNamespace())) {

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Launcher launcher = decorator
                    .decorate(new DummyLauncher(new StreamTaskListener(new TeeOutputStream(out, System.out))), null);
            ContainerExecProc proc = (ContainerExecProc) launcher
                    .launch(launcher.new ProcStarter().pwd("/tmp").cmds(cmd).quiet(quiet));
            assertTrue(proc.isAlive());
            int exitCode = proc.joinWithTimeout(10, TimeUnit.SECONDS, StreamTaskListener.fromStderr());
            return new ProcReturn(proc, exitCode, out.toString());
        }
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
