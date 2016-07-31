/*
 * Copyright (C) 2015 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import com.squareup.okhttp.Response;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.*;

public class ContainerExecDecorator extends LauncherDecorator implements Serializable, Closeable {

    private final transient KubernetesClient client;
    private final transient String podName;
    private final transient String containerName;
    private final transient String path;
    private final transient AtomicBoolean alive;
    private final transient CountDownLatch started;
    private final transient CountDownLatch finished;

    private transient ExecWatch watch;
    private transient ContainerExecProc proc;

    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String path, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this.client = client;
        this.podName = podName;
        this.containerName = containerName;
        this.path = path;
        this.alive = alive;
        this.started = started;
        this.finished = finished;
    }

    @Override
    public Launcher decorate(final Launcher launcher, final Node node) {
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                launcher.getListener().getLogger().println("Executing shell script inside container: [" + containerName + "] of pod: [" + podName + "]");
                watch = client.pods().withName(podName)
                        .inContainer(containerName)
                        .redirectingInput()
                        .writingOutput(launcher.getListener().getLogger())
                        .writingError(launcher.getListener().getLogger())
                        .withTTY()
                        .usingListener(new ExecListener() {
                            @Override
                            public void onOpen(Response response) {
                                alive.set(true);
                                started.countDown();
                            }
                            @Override
                            public void onFailure(IOException e, Response response) {
                                alive.set(false);
                                e.printStackTrace(launcher.getListener().getLogger());
                                started.countDown();
                                finished.countDown();
                            }

                            @Override
                            public void onClose(int i, String s) {
                                alive.set(false);
                                started.countDown();
                                finished.countDown();
                            }
                        }).exec();

                waitQuietly(started);

                //We need to get into the project workspace.
                //The workspace is not known in advance, so we have to execute a cd command.
                watch.getInput().write(("cd " + path + NEWLINE).getBytes(UTF_8));
                doExec(watch, launcher.getListener().getLogger(), getCommands(starter));
                proc = new ContainerExecProc(watch, alive, finished);
                return proc;
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                ContainerExecDecorator.this.close();
            }
        };
    }

    @Override
    public void close() throws IOException {
        if (watch != null) {
            watch.close();
        }

        if (proc != null) {
            try {
                proc.kill();
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
    }

    private static void doExec(ExecWatch watch, PrintStream out, String... statements) {
        try {
            out.print("Executing command:");
            for (String stmt : statements) {
                out.print(stmt);
                out.print(SPACE);
                watch.getInput().write((stmt).getBytes(UTF_8));
                watch.getInput().write((SPACE).getBytes(UTF_8));
            }
            out.println();
            //We need to exit so that we know when the command has finished.
            watch.getInput().write(NEWLINE.getBytes(UTF_8));
            watch.getInput().write(EXIT.getBytes(UTF_8));
            watch.getInput().write(NEWLINE.getBytes(UTF_8));
            watch.getInput().flush();
        } catch (Exception e) {
            e.printStackTrace(out);
        }
    }

    private static String[] getCommands(Launcher.ProcStarter starter) {
        List<String> allCommands = new ArrayList<String>();


        boolean first = true;
        for (String cmd : starter.cmds()) {
            if (first && "nohup".equals(cmd)) {
                first = false;
                continue;
            }
            //I shouldn't been doing that, but clearly the script that is passed to us is wrong?
            allCommands.add(cmd.replaceAll("\\$\\$", "\\$"));
            allCommands.add(cmd);
        }
        return allCommands.toArray(new String[allCommands.size()]);
    }

    private static void waitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            //ignore
        }
    }
}
