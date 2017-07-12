package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

/**
 * Handle the liveness of the processes executed in containers, wait for them to finish and process exit codes.
 *
 */
public class ContainerExecProc extends Proc {

    private static final Logger LOGGER = Logger.getLogger(ContainerExecProc.class.getName());

    private final AtomicBoolean alive;
    private final CountDownLatch finished;
    private final ExecWatch watch;
    private final Callable<Integer> exitCode;

    /**
     * 
     * @param watch
     * @param alive
     * @param finished
     * @param exitCode
     *            a way to get the exit code
     */
    public ContainerExecProc(ExecWatch watch, AtomicBoolean alive, CountDownLatch finished,
            Callable<Integer> exitCode) {
        this.watch = watch;
        this.alive = alive;
        this.finished = finished;
        this.exitCode = exitCode;
    }

    @Override
    public boolean isAlive() throws IOException, InterruptedException {
        return alive.get();
    }

    @Override
    public void kill() throws IOException, InterruptedException {
        try {
            // What we actually do is send a ctrl-c to the current process and then exit the shell.
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
        LOGGER.log(Level.FINEST, "Waiting for websocket to close on command finish ({0})", finished);
        finished.await();
        LOGGER.log(Level.FINEST, "Command is finished ({0})", finished);
        try {
            return exitCode.call();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting exit code", e);
            return -1;
        }
    }

    @Override
    public InputStream getStdout() {
        return watch.getOutput();
    }

    @Override
    public InputStream getStderr() {
        return watch.getError();
    }

    @Override
    public OutputStream getStdin() {
        return watch.getInput();
    }

}
