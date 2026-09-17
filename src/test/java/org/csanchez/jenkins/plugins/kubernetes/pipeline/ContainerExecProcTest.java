package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.model.TaskListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ContainerExecProcTest {

    @Mock
    private ExecWatch execWatch;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * This test validates that {@link hudson.Proc#joinWithTimeout(long, TimeUnit, TaskListener)} is
     * terminated properly when the finished countdown latch is never triggered by the watch.
     */
    @Test(timeout = 20000)
    public void testJoinWithTimeoutFinishCountDown() throws Exception {
        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch finished = new CountDownLatch(1);
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(new ByteArrayOutputStream());
        TaskListener listener = TaskListener.NULL;
        ContainerExecProc proc = new ContainerExecProc(execWatch, alive, finished, stdin, ps);
        int exitCode = proc.joinWithTimeout(1, TimeUnit.SECONDS, listener);
        assertEquals(-1, exitCode);
    }
}
