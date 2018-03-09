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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.output.TeeOutputStream;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.exec.ExitCodeOutputStream;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.exec.FilterOutExitCodeOutputStream;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.proc.CachedProc;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.proc.DeadProc;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
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
import org.apache.commons.io.output.NullOutputStream;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

/**
 * This decorator interacts directly with the Kubernetes exec API to run commands inside a container. It does not use
 * the Jenkins agent to execute commands.
 *
 */
public class ContainerExecDecorator extends LauncherDecorator implements Serializable, Closeable {

    private static final long serialVersionUID = 4419929753433397655L;
    private static final long DEFAULT_CONTAINER_READY_TIMEOUT = 5;
    private static final String CONTAINER_READY_TIMEOUT_SYSTEM_PROPERTY = ContainerExecDecorator.class.getName() + ".containerReadyTimeout";
    private static final long CONTAINER_READY_TIMEOUT = containerReadyTimeout();
    private static final String COOKIE_VAR = "JENKINS_SERVER_COOKIE";
    private static final Logger LOGGER = Logger.getLogger(ContainerExecDecorator.class.getName());
    private static final String DEFAULT_SHELL="/bin/sh";

    private transient KubernetesClient client;


    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private transient List<Closeable> closables;
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private transient Map<Integer, ContainerExecProc> processes = new HashMap<Integer, ContainerExecProc>();

    private String podName;
    private String namespace;
    private String containerName;
    private EnvironmentExpander environmentExpander;
    private EnvVars globalVars;
    private FilePath ws;
    private EnvVars rcEnvVars;
    private String shell;

    public ContainerExecDecorator() {
    }

    @Deprecated
    public ContainerExecDecorator(KubernetesClient client, String podName, String containerName, String namespace, EnvironmentExpander environmentExpander, FilePath ws) {
        this.client = client;
        this.podName = podName;
        this.namespace = namespace;
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

    public KubernetesClient getClient() {
        return client;
    }

    public void setClient(KubernetesClient client) {
        this.client = client;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
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

    @Override
    public Launcher decorate(final Launcher launcher, final Node node) {
        return new Launcher.DecoratedLauncher(launcher) {
            @Override
            public Proc launch(ProcStarter starter) throws IOException {
                LOGGER.log(Level.FINEST, "Launch proc with environment: {0}", Arrays.toString(starter.envs()));
                boolean quiet = starter.quiet();
                FilePath pwd = starter.pwd();
                List<String> procStarter = Arrays.asList(starter.envs());
                List<String> cmdEnvs = new ArrayList<String>();
                // One issue that cropped up was that when executing sh commands, we would get the jnlp agent's injected
                // environment variables as well, causing obvious problems such as JAVA_HOME being overwritten. The
                // unsatisfying answer was to check for the presence of JENKINS_HOME in the cmdenvs and skip if present.
                // check if the cmd is sourced from Jenkins, rather than another plugin;
                // Currently, build level properties will be provided by the Run Context anyways.
                boolean javaHome_detected = false;
                for (String env : procStarter) {
                    if (env.equalsIgnoreCase("JAVA_HOME")) {
                        LOGGER.log(Level.FINEST, "Detected JAVA_HOME in {0}", env);
                        javaHome_detected = true;
                        break;
                    }
                }
                if (!javaHome_detected) {
                    cmdEnvs = procStarter;
                }
                String[] commands = getCommands(starter);
         
                return doLaunch(quiet, cmdEnvs.toArray(new String[cmdEnvs.size()]), starter.stdout(), starter.stderr(), pwd,  commands);
            }

            private Proc doLaunch(boolean quiet, String [] cmdEnvs,  OutputStream outputForCaller, OutputStream errorForCaller, FilePath pwd, String... commands) throws IOException {
                if (processes == null) {
                    processes = new HashMap<>();
                }
                //check ifits the actual script or the ProcessLiveness check.
                int p = readPidFromPsCommand(commands);
                //if it is a liveness check, try to find the actual process to avoid doing multiple execs.
                if (p == 9999) {
                    return new DeadProc();
                } else if (p > 0 && processes.containsKey(p)) {
                    LOGGER.log(Level.INFO, "Retrieved process from cache with pid:[ " + p + "].");
                    return new CachedProc(processes.get(p));
                }

                waitUntilContainerIsReady();

                final CountDownLatch started = new CountDownLatch(1);
                final CountDownLatch finished = new CountDownLatch(1);
                final AtomicBoolean alive = new AtomicBoolean(false);

                if (outputForCaller == null) {
                    outputForCaller = new NullOutputStream();
                }

                if (errorForCaller == null) {
                    errorForCaller = new NullOutputStream();
                }

                PrintStream logger = launcher.getListener().getLogger();
                // Do not send this command to the output when in quiet mode
                if (quiet) {
                    logger = new PrintStream(new NullOutputStream(), false, StandardCharsets.UTF_8.toString());
                }

                final OutputStream teeFilteredOutputStream = new TeeOutputStream(outputForCaller, logger);

                final FilterOutExitCodeOutputStream filteringOutputStream = new FilterOutExitCodeOutputStream(
                        teeFilteredOutputStream);

                // we need to keep the last bytes in the stream to parse the exit code as it is printed there
                // so we use a buffer
                final ExitCodeOutputStream exitCodeOutputStream = new ExitCodeOutputStream();

                final OutputStream stdOut = new TeeOutputStream(filteringOutputStream, exitCodeOutputStream);
                final OutputStream stdErr = new TeeOutputStream(errorForCaller, logger);

                Execable<String, ExecWatch> execable = client.pods().inNamespace(namespace).withName(podName).inContainer(containerName)
                        .redirectingInput().writingOutput(stdOut).writingError(stdErr)
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
                    //get global vars here, run the export first as they'll get overwritten.
                    if (globalVars != null) {
                            this.setupEnvironmentVariable(globalVars, watch);
                    }

                    if(rcEnvVars != null) {
                        this.setupEnvironmentVariable(rcEnvVars, watch);
                    }

                    EnvVars envVars = new EnvVars();
                    if (environmentExpander != null) {
                        environmentExpander.expand(envVars);
                    }

                    //setup specific command envs passed into cmd
                    if (cmdEnvs != null) {
                        LOGGER.log(Level.FINEST, "Launching with env vars: {0}", Arrays.toString(cmdEnvs));
                        for (String cmdEnv : cmdEnvs) {
                            envVars.addLine(cmdEnv);
                        }
                    }

                    this.setupEnvironmentVariable(envVars, watch);
                    doExec(watch, commands);

                    if (closables == null) {
                        closables = new ArrayList<>();
                    }

                    int pid = readPidFromPidFile(commands);
                    LOGGER.log(Level.INFO, "Created process inside pod: ["+podName+"], container: ["+containerName+"] with pid:["+pid+"]");
                    ContainerExecProc proc = new ContainerExecProc(watch, alive, finished, exitCodeOutputStream::getExitCode, filteringOutputStream);
                    processes.put(pid, proc);
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
                        "sh", "-c", "kill \\`grep -l '" + COOKIE_VAR + "=" + cookie  +"' /proc/*/environ | cut -d / -f 3 \\`"
                ).join();

                getListener().getLogger().println("kill finished with exit code " + exitCode);
            }

            private void setupEnvironmentVariable(EnvVars vars, ExecWatch watch) throws IOException {
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    //Check that key is bash compliant.
                    if (entry.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                            watch.getInput().write(
                                    String.format(
                                            "if [ -z ${%s+x} ];then export %s='%s'; fi%s",
                                            entry.getKey(),
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

    private static void doExec(ExecWatch watch, String... statements) throws IOException {
        try {
            LOGGER.log(Level.FINE, "Executing command: ");
            StringBuilder sb = new StringBuilder();
            for (String stmt : statements) {
                String s = String.format("\"%s\" ", stmt);
                sb.append(s);
                LOGGER.log(Level.FINE, s);
                watch.getInput().write(s.getBytes(StandardCharsets.UTF_8));
            }
            sb.append(NEWLINE);
            watch.getInput().write(NEWLINE.getBytes(StandardCharsets.UTF_8));

            // get the command exit code and print it padded so it is easier to parse in ContainerExecProc
            // We need to exit so that we know when the command has finished.
            sb.append(ExitCodeOutputStream.EXIT_COMMAND);
            LOGGER.log(Level.FINE, ExitCodeOutputStream.EXIT_COMMAND);
            LOGGER.log(Level.FINEST, "Executing command: {0}", sb);
            // a hack only for sshagent. it writes out output after the exitcode has been printed if we don't wait a bit
            if ((statements.length >= 1 && statements[0].equals("ssh-add"))
                    || (statements.length == 2 && statements[0].equals("ssh-agent") && statements[1].equals("-k"))) {
                String sleepExitCommand = "tmp_exit_status=$?; sleep 3; printf \"" + ExitCodeOutputStream.EXIT_COMMAND_TXT + " %3d\" $tmp_exit_status; " + EXIT + NEWLINE;
                watch.getInput().write(sleepExitCommand.getBytes(StandardCharsets.UTF_8));
            } else {
                watch.getInput().write(ExitCodeOutputStream.EXIT_COMMAND.getBytes(StandardCharsets.UTF_8));
            }

            watch.getInput().flush();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "IOException during executing command", e);
            throw e;
        }
    }

    static int readPidFromPsCommand(String... commands) {
        if (commands.length == 4 && "ps".equals(commands[0]) && "-o".equals(commands[1]) && commands[2].equals("pid=")) {
            return Integer.parseInt(commands[3]);
        }


        if (commands.length == 4 && "ps".equals(commands[0]) && "-o".equals(commands[1]) && commands[2].startsWith("-pid")) {
            return Integer.parseInt(commands[3]);
        }
        return -1;
    }


    private synchronized int readPidFromPidFile(String... commands) throws IOException, InterruptedException {
        int pid = -1;
        String pidFilePath = readPidFile(commands);
        if (pidFilePath == null) {
            return pid;
        }
        FilePath pidFile = ws.child(pidFilePath);
        for (int w = 0; w < 10 && !pidFile.exists(); w++) {
            try {
                wait(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        if (pidFile.exists()) {
            try {
                pid = Integer.parseInt(pidFile.readToString().trim());
            } catch (NumberFormatException x) {
                throw new IOException("corrupted content in " + pidFile + ": " + x, x);
            }
        }
        return pid;
    }

    @CheckForNull
    static String readPidFile(String... commands) {
        if (commands.length >= 4 && "nohup".equals(commands[0]) && "sh".equals(commands[1]) && commands[2].equals("-c") && commands[3].startsWith("echo \\$\\$ >")) {
            return commands[3].substring(13, commands[3].indexOf(";") - 1);
        }
        return null;
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
}
