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


import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Computer;
import hudson.model.Node;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import okhttp3.Response;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.EXIT;
import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.NEWLINE;

/**
 * This decorator interacts directly with the Kubernetes exec API to run commands inside a container. It does not use
 * the Jenkins agent to execute commands.
 *
 */
public class ContainerExecDecorator extends LauncherDecorator implements Serializable, Closeable {

    private static final long serialVersionUID = 4419929753433397655L;
    private static final long DEFAULT_CONTAINER_READY_TIMEOUT = 5;
    private static final String CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY = ContainerExecDecorator.class.getName() + ".containerReadyTimeout";

    private static final String WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY = ContainerExecDecorator.class.getName()
            + ".websocketConnectionTimeout";
    /** time to wait in seconds for websocket to connect */
    private static final int WEBSOCKET_CONNECTION_TIMEOUT = Integer
            .getInteger(WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY, 30);
    private static final long CONTAINER_READY_TIMEOUT = containerReadyTimeout();
    private static final String COOKIE_VAR = "JENKINS_SERVER_COOKIE";

    private static final Logger LOGGER = Logger.getLogger(ContainerExecDecorator.class.getName());
    private static final String DEFAULT_SHELL = "sh";

    /**
     * stdin buffer size for commands sent to Kubernetes exec api. A low value will cause slowness in commands executed.
     * A higher value will consume more memory
     */
    private static final int STDIN_BUFFER_SIZE = Integer
            .getInteger(ContainerExecDecorator.class.getName() + ".stdinBufferSize", 2 * 1024);

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private transient List<Closeable> closables;

    private String containerName;
    private EnvironmentExpander environmentExpander;
    private EnvVars globalVars;
    /** @deprecated no longer used */
    @Deprecated
    private FilePath ws;
    private EnvVars rcEnvVars;
    private String shell;
    private KubernetesNodeContext nodeContext;

    public ContainerExecDecorator() {
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String namespace, EnvironmentExpander environmentExpander, FilePath ws) {
        this.containerName = containerName;
        this.environmentExpander = environmentExpander;
        this.ws = ws;
        this.shell = DEFAULT_SHELL;
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String namespace, EnvironmentExpander environmentExpander) {
        this(client, podName, containerName, namespace, environmentExpander, null);
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String namespace) {
        this(client, podName, containerName, namespace, null, null);
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished, String namespace) {
        this(client, podName, containerName, namespace, null, null);
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this(client, podName, containerName, (String) null, null, null);
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String path, AtomicBoolean alive, CountDownLatch started, CountDownLatch finished) {
        this(client, podName, containerName, (String) null, null, null);
    }

    @Deprecated
    public KubernetesClient getClient() {
        try {
            return nodeContext.connectToCloud();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setClient(KubernetesClient client) {
        // NOOP
    }

    @Deprecated
    // TODO make private
    public String getPodName() {
        try {
            return getNodeContext().getPodName();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setPodName(String podName) {
        // NOOP
    }

    @Deprecated
    // TODO make private
    public String getNamespace() {
        try {
            return getNodeContext().getNamespace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setNamespace(String namespace) {
        // NOOP
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public EnvironmentExpander getEnvironmentExpander() {
        return environmentExpander;
    }

    public void setEnvironmentExpander(EnvironmentExpander environmentExpander) {
        this.environmentExpander = environmentExpander;
    }

    public EnvVars getGlobalVars() {
        return globalVars;
    }

    public void setGlobalVars(EnvVars globalVars) {
        this.globalVars = globalVars;
    }

    public void setRunContextEnvVars(EnvVars rcVars) {
        this.rcEnvVars = rcVars;
    }

    public EnvVars getRunContextEnvVars() {
        return this.rcEnvVars;
    }

    /** @deprecated unused */
    @Deprecated
    public FilePath getWs() {
        return ws;
    }

    public void setWs(FilePath ws) {
        this.ws = ws;
    }

    public String getShell() {
        return shell == null? DEFAULT_SHELL:shell;
    }

    public void setShell(String shell) {
        this.shell = shell;
    }

    public KubernetesNodeContext getNodeContext() {
        return nodeContext;
    }

    public void setNodeContext(KubernetesNodeContext nodeContext) {
        this.nodeContext = nodeContext;
    }

    @Override
    public Launcher decorate(final Launcher launcher, final Node node) {
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                LOGGER.log(Level.FINEST, "Launch proc with environment: {0}", Arrays.toString(starter.envs()));
                String[] envVars = starter.envs();
                if (node != null) { // It seems this is possible despite the method javadoc saying it is non-null
                    final Computer computer = node.toComputer();
                    if (computer != null) {
                        List<String> resultEnvVar = new ArrayList<>();
                        try {
                            EnvVars environment = computer.getEnvironment();
                            String[] envs = starter.envs();
                            for (String keyValue : envs) {
                                String[] split = keyValue.split("=", 2);
                                if (!split[1].equals(environment.get(split[0]))) {
                                    // Only keep environment variables that differ from Computer's environment
                                    resultEnvVar.add(keyValue);
                                }
                            }
                            envVars = resultEnvVar.toArray(new String[resultEnvVar.size()]);
                        } catch (InterruptedException e) {
                            throw new IOException("Unable to retrieve environment variables", e);
                        }
                    }
                }
                return doLaunch(starter.quiet(), envVars, starter.stdout(), starter.pwd(), starter.masks(),
                        getCommands(starter));
            }

            private Proc doLaunch(boolean quiet, String[] cmdEnvs, OutputStream outputForCaller, FilePath pwd,
                    boolean[] masks, String... commands) throws IOException {
                waitUntilPodContainersAreReady();

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

                // Send to proc caller as well if they sent one
                if (outputForCaller != null && !outputForCaller.equals(stream)) {
                    stream = new TeeOutputStream(outputForCaller, stream);
                }
                ByteArrayOutputStream error = new ByteArrayOutputStream();

                String msg = "Executing shell script inside container [" + containerName + "] of pod [" + getPodName() + "]";
                LOGGER.log(Level.FINEST, msg);
                printStream.println(msg);

                if (closables == null) {
                    closables = new ArrayList<>();
                }

                Execable<String, ExecWatch> execable = getClient().pods().inNamespace(getNamespace()).withName(getPodName()).inContainer(containerName) //
                        .redirectingInput(STDIN_BUFFER_SIZE) // JENKINS-50429
                        .writingOutput(stream).writingError(stream).writingErrorChannel(error)
                        .usingListener(new ExecListener() {
                            @Override
                            public void onOpen(Response response) {
                                alive.set(true);
                                started.countDown();
                                LOGGER.log(Level.FINEST, "onOpen : {0}", finished);
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
                    watch = execable.exec(getShell());
                } catch (KubernetesClientException e) {
                    if (e.getCause() instanceof InterruptedException) {
                        throw new IOException(
                                "Interrupted while starting websocket connection, you should increase the Max connections to Kubernetes API",
                                e);
                    } else {
                        throw e;
                    }
                } catch (RejectedExecutionException e) {
                    throw new IOException(
                            "Connection was rejected, you should increase the Max connections to Kubernetes API", e);
                }

                boolean hasStarted = false;
                try {
                    // prevent a wait forever if the connection is closed as the listener would never be called
                    hasStarted = started.await(WEBSOCKET_CONNECTION_TIMEOUT, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    closeWatch(watch);
                    throw new IOException(
                            "Interrupted while waiting for websocket connection, you should increase the Max connections to Kubernetes API",
                            e);
                }

                if (!hasStarted) {
                    closeWatch(watch);
                    throw new IOException("Timed out waiting for websocket connection. "
                            + "You should increase the value of system property "
                            + WEBSOCKET_CONNECTION_TIMEOUT_SYSTEM_PROPERTY + " currently set at "
                            + WEBSOCKET_CONNECTION_TIMEOUT + " seconds");
                }

                try {
                    OutputStream stdin = watch.getInput();
                    if (pwd != null) {
                        // We need to get into the project workspace.
                        // The workspace is not known in advance, so we have to execute a cd command.
                        stdin.write(String.format("cd \"%s\"%s", pwd, NEWLINE).getBytes(StandardCharsets.UTF_8));
                    }

                    EnvVars envVars = new EnvVars();

                    //get global vars here, run the export first as they'll get overwritten.
                    if (globalVars != null) {
                        envVars.overrideAll(globalVars);
                    }

                    if(rcEnvVars != null) {
                        envVars.overrideAll(rcEnvVars);
                    }

                    if (environmentExpander != null) {
                        environmentExpander.expand(envVars);
                    }

                    //setup specific command envs passed into cmd
                    if (cmdEnvs != null) {
                        for (String cmdEnv : cmdEnvs) {
                            envVars.addLine(cmdEnv);
                        }
                    }

                    LOGGER.log(Level.FINEST, "Launching with env vars: {0}", envVars.toString());

                    this.setupEnvironmentVariable(envVars, stdin);

                    doExec(stdin, printStream, masks, commands);

                    LOGGER.log(Level.INFO, "Created process inside pod: [" + getPodName() + "], container: ["
                            + containerName + "]");
                    ContainerExecProc proc = new ContainerExecProc(watch, alive, finished, stdin, error);
                    closables.add(proc);
                    return proc;
                } catch (InterruptedException ie) {
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
                        true, null, null, null, null,
                        // TODO Windows
                        "sh", "-c", "kill \\`grep -l '" + COOKIE_VAR + "=" + cookie  +"' /proc/*/environ | cut -d / -f 3 \\`"
                ).join();

                getListener().getLogger().println("kill finished with exit code " + exitCode);
            }

            private void setupEnvironmentVariable(EnvVars vars, OutputStream out) throws IOException {
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    //Check that key is bash compliant.
                    if (entry.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                            out.write(
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

            private void waitUntilPodContainersAreReady() throws IOException {
                LOGGER.log(Level.FINEST, "Waiting until pod containers are ready: {0}/{1}",
                        new String[] { getNamespace(), getPodName() });
                try {
                    Pod pod = getClient().pods().inNamespace(getNamespace()).withName(getPodName())
                            .waitUntilReady(CONTAINER_READY_TIMEOUT, TimeUnit.MINUTES);
                    LOGGER.log(Level.FINEST, "Pod is ready: {0}/{1}", new String[] { getNamespace(), getPodName() });

                    if (pod == null || pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                        throw new IOException("Failed to execute shell script inside container " +
                                "[" + containerName + "] of pod [" + getPodName() + "]." +
                                "Failed to get container status");
                    }

                    for (ContainerStatus info : pod.getStatus().getContainerStatuses()) {
                        if (info.getName().equals(containerName)) {
                            if (info.getReady()) {
                                return;
                            } else {
                                // container died in the meantime
                                throw new IOException("container [" + containerName + "] of pod [" + getPodName() + "] is not ready, state is " + info.getState());
                            }
                        }
                    }
                    throw new IOException("container [" + containerName + "] does not exist in pod [" + getPodName() + "]");
                } catch (InterruptedException | KubernetesClientTimeoutException e) {
                    throw new IOException("Failed to execute shell script inside container " +
                            "[" + containerName + "] of pod [" + getPodName() + "]." +
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

    private static void doExec(OutputStream stdin, PrintStream out, boolean[] masks, String... statements) {
        try {
            out.print("Executing command: ");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < statements.length; i++) {
                String s = String.format("\"%s\" ", statements[i]);
                if (masks != null && masks[i]) {
                    sb.append("******** ");
                    out.print("******** ");
                } else {
                    sb.append(s);
                    out.print(s);
                }
                stdin.write(s.getBytes(StandardCharsets.UTF_8));
            }
            sb.append(NEWLINE);
            out.println();
            stdin.write(NEWLINE.getBytes(StandardCharsets.UTF_8));

            // get the command exit code and print it padded so it is easier to parse in ContainerExecProc
            // We need to exit so that we know when the command has finished.
            sb.append(EXIT + NEWLINE);
            out.print(EXIT + NEWLINE);
            LOGGER.log(Level.FINEST, "Executing command: {0}", sb);
            stdin.write((EXIT + NEWLINE).getBytes(StandardCharsets.UTF_8));

            out.flush();
            stdin.flush();
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

    @Deprecated
    public void setKubernetesClient(KubernetesClient client) {
        // NOOP
    }
}
