package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.Proc;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContainerExecProc extends Proc {

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
        //What we actually do is send a ctrl-c to the current process and then exit the shell.
        watch.getInput().write(Constants.CTRL_C);
        watch.getInput().write(Constants.EXIT.getBytes(Constants.UTF_8));
        watch.getInput().write(Constants.NEWLINE.getBytes(Constants.UTF_8));
        watch.getInput().flush();
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
