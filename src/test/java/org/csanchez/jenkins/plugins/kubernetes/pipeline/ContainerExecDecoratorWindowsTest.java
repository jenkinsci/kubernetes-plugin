/*
 * The MIT License
 *
 * Copyright (c) 2022, CloudBees Inc.
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

import hudson.Launcher;
import hudson.Launcher.DummyLauncher;
import hudson.Launcher.ProcStarter;
import hudson.model.Node;
import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.RandomStringUtils;
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
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.recipes.WithTimeout;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.WINDOWS_1809_BUILD;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assumeKubernetes;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assumeWindows;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.setupCloud;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContainerExecDecoratorWindowsTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private KubernetesCloud cloud;
    private static KubernetesClient client;
    private static final Pattern PID_PATTERN = Pattern.compile("^((?:\\[\\d+\\] )?pid is \\d+)$", Pattern.MULTILINE);

    private ContainerExecDecorator decorator;
    private Pod pod;
    private KubernetesSlave agent;

    @Rule
    public LoggerRule containerExecLogs = new LoggerRule()
            .record(Logger.getLogger(ContainerExecDecorator.class.getName()), Level.ALL)
            .record(Logger.getLogger(KubernetesClientProvider.class.getName()), Level.ALL);

    @Rule
    public TestName name = new TestName();

    @BeforeClass
    public static void setUpClass() throws Exception {
        assumeKubernetes();
        assumeWindows(WINDOWS_1809_BUILD);
    }

    @Before
    public void configureCloud() throws Exception {
        cloud = setupCloud(this, name);
        client = cloud.connect();
        deletePods(client, getLabels(this, name), false);

        String image = "mcr.microsoft.com/windows:" + WINDOWS_1809_BUILD + ".2686";
        String containerName = "container";
        String podName = "test-command-execution-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        pod = client.pods().create(new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withLabels(getLabels(this, name))
                .endMetadata()
                .withNewSpec()
                    .withContainers(new ContainerBuilder()
                                .withName(containerName)
                                .withImage(image)
                                .withCommand("powershell")
                                .withArgs("Start-Sleep", "2147483")
                            .build())
                    .addToNodeSelector("kubernetes.io/os", "windows")
                    .addToNodeSelector("node.kubernetes.io/windows-build", WINDOWS_1809_BUILD)
                    .withTerminationGracePeriodSeconds(0L)
                .endSpec().build());

        System.out.println("Created pod: " + pod.getMetadata().getName());
        Pod checkPod = client.pods().withName(podName).waitUntilReady(10, TimeUnit.MINUTES);
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

    @Test
    @WithTimeout(value = 900) // in case we need to pull windows docker image
    public void testCommandExecution() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProcReturn r = execCommandInContainer("container", null, false, output, "where", "cmd.exe");
        assertEquals("C:\\Windows\\System32\\cmd.exe\r\n", output.toString(StandardCharsets.UTF_8.name()));
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @WithTimeout(value = 900) // in case we need to pull windows docker image
    public void testCommandExecutionNoOutput() throws Exception {
        ProcReturn r = execCommandInContainer("container", null, false, null, "where", "cmd.exe");
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    @Test
    @WithTimeout(value = 900) // in case we need to pull windows docker image
    public void testQuietCommandExecution() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ProcReturn r = execCommandInContainer("container", null, true, output, "echo", "pid is 9999");
        String out = output.toString(StandardCharsets.UTF_8.name());
        assertFalse("Output should not contain command: " + out, PID_PATTERN.matcher(out).find());
        assertEquals(0, r.exitCode);
        assertFalse(r.proc.isAlive());
    }

    private ProcReturn execCommandInContainer(String containerName, Node node, boolean quiet, OutputStream outputForCaller, String... cmd) throws Exception {
        decorator.setContainerName(containerName);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DummyLauncher dummyLauncher = new DummyLauncher(new StreamTaskListener(new TeeOutputStream(out, System.out))){
            @Override
            public boolean isUnix() {
                return false;
            }
        };
        Launcher launcher = decorator.decorate(dummyLauncher, node);
        Map<String, String> envs = new HashMap<>(100);
        for (int i = 0; i < 50; i++) {
            envs.put("aaaaaaaa" + i, "bbbbbbbb");
        }
        envs.put("workingDir1", "C:\\agent");

        ProcStarter procStarter = launcher.new ProcStarter().pwd("C:\\").cmds(cmd).envs(envs).quiet(quiet);
        if (outputForCaller != null) {
            procStarter.stdout(outputForCaller);
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
