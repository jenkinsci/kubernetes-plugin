package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.*;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;
import org.apache.commons.io.output.NullPrintStream;

/**
 * Handle the liveness of the processes executed in containers, wait for them to finish and process exit codes.
 *
 */
public class ContainerExecProc extends Proc implements Closeable, Runnable {

    private static final Logger LOGGER = Logger.getLogger(ContainerExecProc.class.getName());

    private final AtomicBoolean alive;
    private final CountDownLatch finished;
    private final ExecWatch watch;
    private final OutputStream stdin;

    private final PrintStream printStream;

    @Deprecated
    public ContainerExecProc(
            ExecWatch watch, AtomicBoolean alive, CountDownLatch finished, Callable<Integer> exitCode) {
        this(watch, alive, finished, (OutputStream) null, (PrintStream) null);
    }

    @Deprecated
    public ContainerExecProc(
            ExecWatch watch, AtomicBoolean alive, CountDownLatch finished, ByteArrayOutputStream error) {
        this(watch, alive, finished, (OutputStream) null, (PrintStream) null);
    }

    @Deprecated
    public ContainerExecProc(
            ExecWatch watch,
            AtomicBoolean alive,
            CountDownLatch finished,
            OutputStream stdin,
            ByteArrayOutputStream error) {
        this(watch, alive, finished, stdin, (PrintStream) null);
    }

    public ContainerExecProc(
            ExecWatch watch,
            AtomicBoolean alive,
            CountDownLatch finished,
            OutputStream stdin,
            PrintStream printStream) {
        this.watch = watch;
        this.stdin = stdin == null ? watch.getInput() : stdin;
        this.alive = alive;
        this.finished = finished;
        this.printStream = printStream == null ? NullPrintStream.NULL_PRINT_STREAM : printStream;
        Timer.get().schedule(this, 1, TimeUnit.MINUTES);
    }

    @Override
    public boolean isAlive() {
        return alive.get();
    }

    @Override
    public void kill() throws IOException, InterruptedException {
        try {
            // What we actually do is send a ctrl-c to the current process and then exit the shell.
            stdin.write(CTRL_C);
            stdin.write(EXIT.getBytes(StandardCharsets.UTF_8));
            stdin.write(NEWLINE.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
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
            if (finished.await(1, TimeUnit.MINUTES)) {
                LOGGER.log(Level.FINEST, "Command is finished (the count reached zero)");
            } else {
                LOGGER.log(Level.FINEST, "Command is finished (waiting time elapsed before the count reached zero)");
            }

            CompletableFuture<Integer> exitCodeFuture = watch.exitCode();

            if (exitCodeFuture == null) {
                LOGGER.log(Level.FINEST, "exitCodeFuture is null.");
                printStream.print("exitCodeFuture is null.");
                return -1;
            }

            Integer exitCode = exitCodeFuture.get();

            if (exitCode == null) {
                LOGGER.log(
                        Level.FINEST,
                        "The container exec watch was closed before it could obtain an exit code from the process.");
                printStream.print(
                        "The container exec watch was closed before it could obtain an exit code from the process.");
                return -1;
            }
            return exitCode;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                LOGGER.log(Level.FINEST, "ExecutionException occurred while waiting for exit code", cause);
                printStream.printf("ExecutionException occurred while waiting for exit code: %s%n", cause);
            } else {
                LOGGER.log(Level.FINEST, "ExecutionException occurred while waiting for exit code", e);
                printStream.printf("ExecutionException occurred while waiting for exit code: %s%n", e);
            }
            return -1;
        } finally {
            close();
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
        return stdin;
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

    @Override
    public void run() {
        if (!isAlive()) {
            LOGGER.fine("process is no longer alive");
            return;
        }
        try {
            stdin.write(NEWLINE.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            LOGGER.fine("sent a newline to keep socket alive");
            Timer.get().schedule(this, 1, TimeUnit.MINUTES);
        } catch (IOException x) {
            LOGGER.log(Level.FINE, "socket keepalive failed", x);
        }
    }
}
