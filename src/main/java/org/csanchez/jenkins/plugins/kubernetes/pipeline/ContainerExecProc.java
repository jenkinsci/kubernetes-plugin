package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

/**
 * Handle the liveness of the processes executed in containers, wait for them to finish and process exit codes.
 *
 */
public class ContainerExecProc extends Proc implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(ContainerExecProc.class.getName());

    private final AtomicBoolean alive;
    private final CountDownLatch finished;
    private final ExecWatch watch;

    @Deprecated
    public ContainerExecProc(ExecWatch watch, AtomicBoolean alive, CountDownLatch finished,
            Callable<Integer> exitCode) {
        this(watch, alive, finished);
    }

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
            // What we actually do is send a ctrl-c to the current process and then exit the shell.
            watch.getStdinPipe().write(CTRL_C);
            watch.getStdinPipe().write(EXIT.getBytes(StandardCharsets.UTF_8));
            watch.getStdinPipe().write(NEWLINE.getBytes(StandardCharsets.UTF_8));
            watch.getStdinPipe().flush();
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Proc kill failed, ignoring", e);
        } finally {
            close();
        }
    }

    @Override
    public int join() throws IOException, InterruptedException {
        try {
            LOGGER.log(Level.FINEST, "Waiting for websocket to close on command finish ({0})", finished);
            finished.await();
            LOGGER.log(Level.FINEST, "Command is finished ({0})", finished);
            InputStream errorPipe = watch.getErrorPipe();
            String s = IOUtils.toString(errorPipe);
            // TODO
            return 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting exit code", e);
            return -1;
        } finally {
            close();
        }
    }

    @Override
    public InputStream getStdout() {
        return watch.getStdoutPipe();
    }

    @Override
    public InputStream getStderr() {
        return watch.getStderrPipe();
    }

    @Override
    public OutputStream getStdin() {
        return watch.getInput();
    }

    @Override
    public void close() throws IOException {
        try {
            // We are calling explicitly close, in order to cleanup websockets and threads (are not closed implicitly).
            watch.close();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "failed to close watch", e);
        }
    }
}
