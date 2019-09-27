package org.csanchez.jenkins.plugins.kubernetes.pipeline;


import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import jenkins.util.Timer;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.CTRL_C;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.EXIT;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.NEWLINE;

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
    private final ByteArrayOutputStream error;

    @Deprecated
    public ContainerExecProc(ExecWatch watch, AtomicBoolean alive, CountDownLatch finished,
            Callable<Integer> exitCode) {
        this(watch, alive, finished, null, new ByteArrayOutputStream());
    }

    @Deprecated
    public ContainerExecProc(ExecWatch watch, AtomicBoolean alive, CountDownLatch finished,
            ByteArrayOutputStream error) {
        this(watch, alive, finished, null, error);
    }

    public ContainerExecProc(ExecWatch watch, AtomicBoolean alive, CountDownLatch finished, OutputStream stdin,
            ByteArrayOutputStream error) {
        this.watch = watch;
        this.stdin = stdin == null ? watch.getInput() : stdin;
        this.alive = alive;
        this.finished = finished;
        this.error = error;
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
            finished.await();
            LOGGER.log(Level.FINEST, "Command is finished ({0})", finished);
            if (error.size() == 0) {
                return 0;
            } else {
                // {"metadata":{},"status":"Success"}
                // or
                // {"metadata":{},"status":"Failure",
                //   "message":"command terminated with non-zero exit code: Error executing in Docker Container: 127",
                //   "reason":"NonZeroExitCode",
                //   "details":{"causes":[{"reason":"ExitCode","message":"127"}]}}
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode errorJson = mapper.readTree(error.toByteArray());
                    if ("Success".equalsIgnoreCase(errorJson.get("status").asText())) {
                        return 0;
                    }
                    JsonNode causes = errorJson.get("details").get("causes");
                    if (causes.isArray()) {
                        for (JsonNode cause : causes) {
                            if ("ExitCode".equalsIgnoreCase(cause.get("reason").asText(""))) {
                                return cause.get("message").asInt();
                            }
                        }
                    }
                    LOGGER.log(Level.WARNING, "Unable to parse exit code from error message: {0}",
                            error.toString(StandardCharsets.UTF_8.name()));
                    return -1;
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Unable to parse exit code from error message: "
                            + error.toString(StandardCharsets.UTF_8.name()), e);
                    return -1;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error getting exit code", e);
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
