package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

public class ContainerExecProc extends Proc {

    private static final Logger LOGGER = Logger.getLogger(ContainerExecProc.class.getName());

    private final AtomicBoolean alive;
    private final CountDownLatch finished;
    private final ExecWatch watch;

    public ContainerExecProc(ExecWatch watch, AtomicBoolean alive, CountDownLatch finished) {
        this.watch = watch;
        this.alive = alive;
        this.finished = finished;
    }

    @Override
    public boolean isAlive() throws IOException, InterruptedException {
        return alive.get();
    }

    @Override
    public void kill() throws IOException, InterruptedException {
        try {
            //What we actually do is send a ctrl-c to the current process and then exit the shell.
            watch.getInput().write(CTRL_C);
            watch.getInput().write(EXIT.getBytes(StandardCharsets.UTF_8));
            watch.getInput().write(NEWLINE.getBytes(StandardCharsets.UTF_8));
            watch.getInput().flush();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Proc kill failed, ignoring", e);
        }
    }

    @Override
    public int join() throws IOException, InterruptedException {
        finished.await();
        return 1;
    }

    @Override
    public InputStream getStdout() {
        return watch.getOutput();
    }

    @Override
    public InputStream getStderr() {
        return watch.getOutput();
    }

    @Override
    public OutputStream getStdin() {
        return watch.getInput();
    }
}
