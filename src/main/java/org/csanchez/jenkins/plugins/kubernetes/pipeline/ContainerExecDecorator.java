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

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.EnvVars;
import hudson.FilePath;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import org.apache.commons.io.output.TeeOutputStream;

import com.google.common.io.NullOutputStream;

import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.Execable;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

/**
 * This decorator interacts directly with the Kubernetes exec API to run commands inside a container. It does not use
 * the Jenkins slave to execute commands.
 *
 */
public class ContainerExecDecorator extends LauncherDecorator implements Serializable, Closeable {

    private static final long serialVersionUID = 4419929753433397655L;
    private static final long DEFAULT_CONTAINER_READY_TIMEOUT = 5;
    private static final String CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY = ContainerExecDecorator.class.getName() + ".containerReadyTimeout";
    private static final long CONTAINER_READY_TIMEOUT = containerReadyTimeout();
    private static final String COOKIE_VAR = "JENKINS_SERVER_COOKIE";
    private static final String JENKINS_HOME = "JENKINS_HOME=";
    private static final Logger LOGGER = Logger.getLogger(ContainerExecDecorator.class.getName());

    private transient KubernetesClient client;

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private transient List<Closeable> closables;
    private final String podName;
    private final String namespace;
    private final String containerName;
    private final EnvironmentExpander environmentExpander;

    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String namespace, EnvironmentExpander environmentExpander) {
        this.client = client;
        this.podName = podName;
        this.namespace = namespace;
        this.containerName = containerName;
        this.environmentExpander = environmentExpander;
    }

    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String namespace) {
        this(client, podName, containerName, namespace, null);
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished, String namespace) {
        this(client, podName, containerName, namespace, null);
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this(client, podName, containerName, null, null);
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String path, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this(client, podName, containerName, null, null);
    }

    @Override
    public Launcher decorate(final Launcher launcher, final Node node) {
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                boolean quiet = starter.quiet();
                FilePath pwd = starter.pwd();

                String [] cmdEnvs = starter.envs();

                //check if the cmd is sourced from Jenkins, rather than another plugin; if so, skip cmdEnvs as we are getting other environment variables
                for (String cmd : cmdEnvs) {
                    if (cmd.startsWith(JENKINS_HOME)) {
                        cmdEnvs = new String[0];
                        LOGGER.info("Skipping injection of procstarter cmdenvs due to JENKINS_HOME present");
                        break;
                    }
                }
                String [] commands = getCommands(starter);
                return doLaunch(quiet, cmdEnvs, starter.stdout(), pwd,  commands);
            }

            private Proc doLaunch(boolean quiet, String [] cmdEnvs,  OutputStream outputForCaller, FilePath pwd, String... commands) throws IOException {
                waitUntilContainerIsReady();

                final CountDownLatch started = new CountDownLatch(1);
                final CountDownLatch finished = new CountDownLatch(1);
                final AtomicBoolean alive = new AtomicBoolean(false);


                PrintStream printStream = launcher.getListener().getLogger();
                OutputStream stream = printStream;
                // Do not send this command to the output when in quiet mode
                if (quiet) {
                    stream = new NullOutputStream();
                    printStream = new PrintStream(stream, false, StandardCharsets.UTF_8.toString());
                }

                // we need to keep the last bytes in the stream to parse the exit code as it is printed there
                // so we use a buffer
                ExitCodeOutputStream exitCodeOutputStream = new ExitCodeOutputStream();
                // send container output both to the job output and our buffer
                stream = new TeeOutputStream(exitCodeOutputStream, stream);
                // Send to proc caller as well if they sent one
                if (outputForCaller != null) {
                    stream = new TeeOutputStream(outputForCaller, stream);
                }

                String msg = "Executing shell script inside container [" + containerName + "] of pod [" + podName + "]";
                LOGGER.log(Level.FINEST, msg);
                printStream.println(msg);

                Execable<String, ExecWatch> execable = client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                        .redirectingInput().writingOutput(stream).writingError(stream)
                        .usingListener(new ExecListener() {
                            @Override
                            public void onOpen(Response response) {
                                alive.set(true);
                                started.countDown();
                            }

                            @Override
                            public void onFailure(Throwable t, Response response) {
                                alive.set(false);
                                t.printStackTrace(launcher.getListener().getLogger());
                                started.countDown();
                                LOGGER.log(Level.FINEST, "onFailure : {0}", finished);
                                if (finished.getCount() == 0) {
                                    LOGGER.log(Level.WARNING,
                                            "onFailure called but latch already finished. This may be a bug in the kubernetes-plugin");
                                }
                                finished.countDown();
                            }

                            @Override
                            public void onClose(int i, String s) {
                                alive.set(false);
                                started.countDown();
                                LOGGER.log(Level.FINEST, "onClose : {0}", finished);
                                if (finished.getCount() == 0) {
                                    LOGGER.log(Level.WARNING,
                                            "onClose called but latch already finished. This indicates a bug in the kubernetes-plugin");
                                }
                                finished.countDown();
                            }
                        });

                ExecWatch watch;
                try {
                    watch = execable.exec("/bin/sh");
                } catch (KubernetesClientException e) {
                    if (e.getCause() instanceof InterruptedException) {
                        throw new IOException("JENKINS-40825: interrupted while starting websocket connection", e);
                    } else {
                        throw e;
                    }
                }

                try {
                    started.await();
                } catch (InterruptedException e) {
                    closeWatch(watch);
                    throw new IOException("JENKINS-40825: interrupted while waiting for websocket connection", e);
                }

                try {
                    if (pwd != null) {
                        // We need to get into the project workspace.
                        // The workspace is not known in advance, so we have to execute a cd command.
                        watch.getInput().write(
                                String.format("cd \"%s\"%s", pwd, NEWLINE).getBytes(StandardCharsets.UTF_8));

                    }

                    EnvVars envVars = new EnvVars();
                    if (environmentExpander != null) {
                        environmentExpander.expand(envVars);
                    }

                    //setup specific command envs passed into cmd
                    if (cmdEnvs != null) {
                        for (String cmdEnv : cmdEnvs) {
                            envVars.addLine(cmdEnv);
                        }
                    }

                    this.setupEnvironmentVariable(envVars, watch);
                    doExec(watch, printStream, commands);
                    ContainerExecProc proc = new ContainerExecProc(watch, alive, finished, exitCodeOutputStream::getExitCode);
                    if (closables == null) {
                        closables = new ArrayList<>();
                    }
                    closables.add(proc);
                    return proc;
                }  catch (InterruptedException ie) {
                    throw new InterruptedIOException(ie.getMessage());
                } catch (Exception e) {
                    closeWatch(watch);
                    throw e;
                }
            }

            @Override
            public void kill(Map<String, String> modelEnvVars) throws IOException, InterruptedException {
                getListener().getLogger().println("Killing processes");

                String cookie = modelEnvVars.get(COOKIE_VAR);

                int exitCode = doLaunch(
                        true, null, null, null,
                        "sh", "-c", "kill \\`grep -l '" + COOKIE_VAR + "=" + cookie  +"' /proc/*/environ | cut -d / -f 3 \\`"
                ).join();

                getListener().getLogger().println("kill finished with exit code " + exitCode);
            }

            private void setupEnvironmentVariable(EnvVars vars, ExecWatch watch) throws IOException
            {
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    //Check that key is bash compliant.
                    if (entry.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                            watch.getInput().write(
                                    String.format(
                                            "export %s='%s'%s",
                                            entry.getKey(),
                                            entry.getValue().replace("'", "'\\''"),
                                            NEWLINE
                                    ).getBytes(StandardCharsets.UTF_8)
                            );
                        }
                    }
            }

            private void waitUntilContainerIsReady() throws IOException {
                try {
                    Pod pod = client.pods().inNamespace(namespace).withName(podName)
                            .waitUntilReady(CONTAINER_READY_TIMEOUT, TimeUnit.MINUTES);

                    if (pod == null || pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                        throw new IOException("Failed to execute shell script inside container " +
                                "[" + containerName + "] of pod [" + podName + "]." +
                                "Failed to get container status");
                    }

                    for (ContainerStatus info : pod.getStatus().getContainerStatuses()) {
                        if (info.getName().equals(containerName)) {
                            if (info.getReady()) {
                                return;
                            } else {
                                // container died in the meantime
                                throw new IOException("container [" + containerName + "] of pod [" + podName + "] is not ready, state is " + info.getState());
                            }
                        }
                    }
                    throw new IOException("container [" + containerName + "] does not exist in pod [" + podName + "]");
                } catch (InterruptedException | KubernetesClientTimeoutException e) {
                    throw new IOException("Failed to execute shell script inside container " +
                            "[" + containerName + "] of pod [" + podName + "]." +
                            " Timed out waiting for container to become ready!", e);
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        if (closables == null) return;

        for (Closeable closable : closables) {
            try {
                closable.close();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "failed to close {0}");
            }
        }
    }

    private static void doExec(ExecWatch watch, PrintStream out, String... statements) {
        try {
            out.print("Executing command: ");
            StringBuilder sb = new StringBuilder();
            for (String stmt : statements) {
                String s = String.format("\"%s\" ", stmt);
                sb.append(s);
                out.print(s);
                watch.getInput().write(s.getBytes(StandardCharsets.UTF_8));
            }
            sb.append(NEWLINE);
            out.println();
            watch.getInput().write(NEWLINE.getBytes(StandardCharsets.UTF_8));

            // get the command exit code and print it padded so it is easier to parse in ContainerExecProc
            // We need to exit so that we know when the command has finished.
            sb.append(ExitCodeOutputStream.EXIT_COMMAND);
            out.print(ExitCodeOutputStream.EXIT_COMMAND);
            LOGGER.log(Level.FINEST, "Executing command: {0}", sb);
            watch.getInput().write(ExitCodeOutputStream.EXIT_COMMAND.getBytes(StandardCharsets.UTF_8));

            out.flush();
            watch.getInput().flush();
        } catch (IOException e) {
            e.printStackTrace(out);
            throw new RuntimeException(e);
        }
    }

    static String[] getCommands(Launcher.ProcStarter starter) {
        List<String> allCommands = new ArrayList<String>();

        // BourneShellScript.launchWithCookie escapes $ as $$, we convert it to \$
        for (String cmd : starter.cmds()) {
            allCommands.add(cmd.replaceAll("\\$\\$", "\\\\\\$"));
        }
        return allCommands.toArray(new String[allCommands.size()]);
    }

    private static Long containerReadyTimeout() {
        String timeout = System.getProperty(CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY, String.valueOf(DEFAULT_CONTAINER_READY_TIMEOUT));
        try {
            return Long.parseLong(timeout);
        } catch (NumberFormatException e) {
            return DEFAULT_CONTAINER_READY_TIMEOUT;
        }
    }

    private static void closeWatch(ExecWatch watch) {
        try {
            watch.close();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "failed to close watch", e);
        }
    }

    public void setKubernetesClient(KubernetesClient client) {
        this.client = client;
    }

    /**
     * Keeps the last bytes of the output stream to parse the exit code
     */
    static class ExitCodeOutputStream extends OutputStream {

        public static final String EXIT_COMMAND_TXT = "EXITCODE";
        public static final String EXIT_COMMAND = "printf \"" + EXIT_COMMAND_TXT + " %3d\" $?; " + EXIT + NEWLINE;

        private EvictingQueue<Integer> queue = EvictingQueue.create(20);

        public ExitCodeOutputStream() {
        }

        @Override
        public void write(int b) throws IOException {
            queue.add(b);
            byte[] bb = new byte[]{(byte) b};
            System.out.print(new String(bb, StandardCharsets.UTF_8));
        }

        public int getExitCode() {
            ByteBuffer b = ByteBuffer.allocate(queue.size());
            queue.stream().filter(Objects::nonNull).forEach((i) -> b.put((byte) i.intValue()));
            // output ends in a 3 digit padded exit code + newline (13 10)
            // as defined in ContainerExecDecorator#doExec
            // ie. 32 32 49 13 10 for exit code 1
            int i = 1;
            String s = new String(b.array(), StandardCharsets.UTF_8);
            if (s.indexOf(EXIT_COMMAND_TXT) < 0) {
                LOGGER.log(Level.WARNING, "Unable to find \"{0}\" in {1}", new Object[] { EXIT_COMMAND_TXT, s });
                return i;
            }
            // parse the exitcode int printed after EXITCODE
            int start = s.indexOf(EXIT_COMMAND_TXT) + EXIT_COMMAND_TXT.length();
            s = s.substring(start, start + 4).trim();
            try {
                i = Integer.parseInt(s);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Unable to parse exit code as integer: \"{0}\" {1} / {2}",
                        new Object[] { s, queue.toString(), Arrays.toString(b.array()) });
            }
            return i;
        }
    }
}
