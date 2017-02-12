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

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.fabric8.kubernetes.client.Watcher.Action.MODIFIED;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.*;

public class ContainerExecDecorator extends LauncherDecorator implements Serializable, Closeable {

    private static final long serialVersionUID = 4419929753433397655L;
    private static final long DEFAULT_CONTAINER_READY_TIMEOUT = 5;
    private static final String CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY = ContainerExecDecorator.class.getName() + ".containerReadyTimeout";
    private static final long CONTAINER_READY_TIMEOUT = containerReadyTimeout();
    private static final Logger LOGGER = Logger.getLogger(ContainerExecDecorator.class.getName());

    private final transient KubernetesClient client;
    private final String podName;
    private final String containerName;
    private final String path;
    private final AtomicBoolean alive;

    private transient CountDownLatch started;
    private transient CountDownLatch finished;

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
                if (!waitUntilContainerIsReady()) {
                    throw new IOException("Failed to execute shell script inside container " +
                            "[" + containerName + "] of pod [" + podName + "]." +
                            " Timed out waiting for container to become ready!");
                }
                launcher.getListener().getLogger().println("Executing shell script inside container [" + containerName + "] of pod [" + podName + "]");
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
                watch.getInput().write(String.format("cd \"%s\"%s", path, NEWLINE).getBytes(StandardCharsets.UTF_8));
                doExec(watch, launcher.getListener().getLogger(), getCommands(starter));
                proc = new ContainerExecProc(watch, alive, finished);
                return proc;
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                getListener().getLogger().println("Killing process.");
                ContainerExecDecorator.this.close();
            }


            private boolean isContainerReady(Pod pod, String container) {
                if (pod == null || pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                    return false;
                }

                for (ContainerStatus info : pod.getStatus().getContainerStatuses()) {
                    if (info.getName().equals(container) && info.getReady()) {
                        return true;
                    }
                }
                return false;
            }

            private boolean waitUntilContainerIsReady() {
                Pod pod = client.pods().withName(podName).get();

                if (pod == null) {
                    throw new IllegalArgumentException("Container with name:[" + containerName+"] not found in pod:[" + podName + "]");
                }
                if (isContainerReady(pod, containerName)) {
                    return true;
                }

                launcher.getListener().getLogger().println("Waiting for container container [" + containerName + "] of pod [" + podName + "] to become ready.");
                final CountDownLatch latch = new CountDownLatch(1);
                Watcher<Pod> podWatcher = new Watcher<Pod>() {
                    @Override
                    public void eventReceived(Action action, Pod resource) {
                        switch (action) {
                            case MODIFIED:
                                if (isContainerReady(resource, containerName)) {
                                    latch.countDown();
                                }
                                break;
                            default:
                        }
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {

                    }
                };

                try (Watch watch = client.pods().withName(podName).watch(podWatcher)) {
                    if (latch.await(CONTAINER_READY_TIMEOUT, TimeUnit.MINUTES)) {
                        return true;
                    }
                } catch (InterruptedException e) {
                    return false;
                }
                return false;
            }
        };
    }

    @Override
    public void close() throws IOException {
        if (watch != null) {
            try {
                watch.close();
            } catch (IllegalStateException e) {
                LOGGER.log(Level.INFO, "Watch was already closed: {0}", e.getMessage());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing watch", e);
            } finally {
                watch = null;
            }
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
            out.print("Executing command: ");
            for (String stmt : statements) {
                String s = String.format("%s ", stmt);
                out.print(s);
                watch.getInput().write(s.getBytes(StandardCharsets.UTF_8));
            }
            out.println();
            //We need to exit so that we know when the command has finished.
            watch.getInput().write("\nexit\n".getBytes(StandardCharsets.UTF_8));
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

    private static Long containerReadyTimeout() {
        String timeout = System.getProperty(CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY, String.valueOf(DEFAULT_CONTAINER_READY_TIMEOUT));
        try {
            return Long.parseLong(timeout);
        } catch (NumberFormatException e) {
            return DEFAULT_CONTAINER_READY_TIMEOUT;
        }
    }
}
