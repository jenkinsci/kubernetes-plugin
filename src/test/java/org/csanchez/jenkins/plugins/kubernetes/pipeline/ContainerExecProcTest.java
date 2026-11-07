package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ContainerExecProcTest {

    @Test
    void keepaliveSendsNewlineWhileOpen() throws Exception {
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        try (ContainerExecProc proc = newProc(stdin)) {
            proc.run();
            assertThat(stdin.toString(), is("\n"));
        }
    }

    @Test
    void keepaliveStopsAfterClose() throws Exception {
        ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        ContainerExecProc proc = newProc(stdin);
        proc.close();
        proc.run();
        assertThat(stdin.size(), is(0));
        assertThat(proc.isKeepaliveScheduled(), is(false));
    }

    private static ContainerExecProc newProc(ByteArrayOutputStream stdin) {
        return new ContainerExecProc(
                mock(ExecWatch.class), new AtomicBoolean(true), new CountDownLatch(1), stdin, System.out);
    }
}
