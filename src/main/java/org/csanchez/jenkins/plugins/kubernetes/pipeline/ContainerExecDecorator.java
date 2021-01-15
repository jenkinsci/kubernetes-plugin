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
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

import hudson.AbortException;
import io.fabric8.kubernetes.api.model.Container;
import org.apache.commons.io.output.TeeOutputStream;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Computer;
import hudson.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.Execable;
import okhttp3.Response;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Constants.EXIT;

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

    /**
     * stdin buffer size for commands sent to Kubernetes exec api. A low value will cause slowness in commands executed.
     * A higher value will consume more memory
     */
    private static final int STDIN_BUFFER_SIZE = Integer.getInteger(ContainerExecDecorator.class.getName() + ".stdinBufferSize", 16 * 1024);
    /**
     * time in milliseconds to wait for checking whether the process immediately returned
     */
    public static final int COMMAND_FINISHED_TIMEOUT_MS = 200;

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

                // find container working dir
                KubernetesSlave slave = (KubernetesSlave) node;
                FilePath containerWorkingDirFilePath = starter.pwd();
                String containerWorkingDirFilePathStr = containerWorkingDirFilePath != null
                        ? containerWorkingDirFilePath.getRemote() : ContainerTemplate.DEFAULT_WORKING_DIR;
                String containerWorkingDirStr = ContainerTemplate.DEFAULT_WORKING_DIR;
                if (slave != null && slave.getPod().isPresent() && containerName != null) {
                    Optional<Container> container = slave.getPod().get().getSpec().getContainers().stream()
                            .filter(container1 -> container1.getName().equals(containerName))
                            .findAny();
                    Optional<String> containerWorkingDir = Optional.empty();
                    if (container.isPresent() && container.get().getWorkingDir() != null) {
                        containerWorkingDir = Optional.of(container.get().getWorkingDir());
                    }
                    if (containerWorkingDir.isPresent()) {
                        containerWorkingDirStr = containerWorkingDir.get();
                    }

                    if (containerWorkingDir.isPresent() &&
                            containerWorkingDirFilePath != null &&
                            ! containerWorkingDirFilePath.getRemote().startsWith(containerWorkingDirStr)) {
                        // Container has a custom workingDir, updated the pwd to match container working dir
                        containerWorkingDirFilePathStr = containerWorkingDirFilePath.getRemote().replaceFirst(
                                ContainerTemplate.DEFAULT_WORKING_DIR, containerWorkingDirStr);
                        containerWorkingDirFilePath = new FilePath(containerWorkingDirFilePath.getChannel(), containerWorkingDirFilePathStr);
                        LOGGER.log(Level.FINEST, "Modified the pwd to match {0} containers workspace directory : {1}",
                                new String[]{containerName, containerWorkingDirFilePathStr});
                    }
                }

                String[] envVars = starter.envs();
                // modify the working dir on envvars part of starter env vars
                if (!containerWorkingDirStr.equals(ContainerTemplate.DEFAULT_WORKING_DIR)) {
                    for (int i = 0; i < envVars.length; i++) {
                        String keyValue = envVars[i];
                        String[] split = keyValue.split("=", 2);
                        if (split[1].startsWith(ContainerTemplate.DEFAULT_WORKING_DIR)) {
                            // Container has a custom workingDir, update env vars with right workspace folder
                            split[1] = split[1].replaceFirst(ContainerTemplate.DEFAULT_WORKING_DIR, containerWorkingDirStr);
                            envVars[i] = split[0] + "=" + split[1];
                            LOGGER.log(Level.FINEST, "Updated the starter environment variable, key: {0}, Value: {1}",
                                    new String[]{split[0], split[1]});
                        }
                    }
                }

                if (node != null) { // It seems this is possible despite the method javadoc saying it is non-null
                    final Computer computer = node.toComputer();
                    if (computer != null) {
                        List<String> resultEnvVar = new ArrayList<>();
                        try {
                            EnvVars environment = computer.getEnvironment();
                            if (environment != null) {
                                Set<String> overriddenKeys = new HashSet<>();
                                for (String keyValue : envVars) {
                                    String[] split = keyValue.split("=", 2);
                                    if (!split[1].equals(environment.get(split[0]))) {
                                        // Only keep environment variables that differ from Computer's environment
                                        resultEnvVar.add(keyValue);
                                        overriddenKeys.add(split[0]);
                                    }
                                }

                                // modify the working dir on envvars part of Computer
                                if (!containerWorkingDirStr.equals(ContainerTemplate.DEFAULT_WORKING_DIR)) {
                                    for (Map.Entry<String, String> entry : environment.entrySet()) {
                                        if (entry.getValue().startsWith(ContainerTemplate.DEFAULT_WORKING_DIR)
                                                && !overriddenKeys.contains(entry.getKey())) {
                                            // Value should be overridden and is not overridden earlier
                                            String newValue = entry.getValue().replaceFirst(ContainerTemplate.DEFAULT_WORKING_DIR, containerWorkingDirStr);
                                            String keyValue = entry.getKey() + "=" + newValue;
                                            LOGGER.log(Level.FINEST, "Updated the value for envVar, key: {0}, Value: {1}",
                                                    new String[]{entry.getKey(), newValue});
                                            resultEnvVar.add(keyValue);
                                        }
                                    }
                                }
                                envVars = resultEnvVar.toArray(new String[resultEnvVar.size()]);
                            }
                        } catch (InterruptedException e) {
                            throw new IOException("Unable to retrieve environment variables", e);
                        }
                    }
                }
                return doLaunch(starter.quiet(), fixDoubleDollar(envVars), starter.stdout(), containerWorkingDirFilePath, starter.masks(),
                        getCommands(starter, containerWorkingDirFilePathStr));
            }

            private Proc doLaunch(boolean quiet, String[] cmdEnvs, OutputStream outputForCaller, FilePath pwd,
                    boolean[] masks, String... commands) throws IOException {
                final CountDownLatch started = new CountDownLatch(1);
                final CountDownLatch finished = new CountDownLatch(1);
                final AtomicBoolean alive = new AtomicBoolean(false);
                final AtomicLong startAlive = new AtomicLong();
                long startMethod = System.nanoTime();

                PrintStream printStream;
                OutputStream stream;

                // Only output to stdout at the beginning for diagnostics.
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ToggleOutputStream toggleStdout = new ToggleOutputStream(stdout);

                // Do not send this command to the output when in quiet mode
                if (quiet) {
                    stream = toggleStdout;
                    printStream = new PrintStream(stream, true, StandardCharsets.UTF_8.toString());
                } else {
                    printStream = launcher.getListener().getLogger();
                    stream = new TeeOutputStream(toggleStdout, printStream);
                }

                // Send to proc caller as well if they sent one
                if (outputForCaller != null && !outputForCaller.equals(printStream)) {
                    stream = new TeeOutputStream(outputForCaller, stream);
                }
                ByteArrayOutputStream error = new ByteArrayOutputStream();

                String sh = shell != null ? shell : launcher.isUnix() ? "sh" : "cmd";
                String msg = "Executing " + sh + " script inside container " + containerName + " of pod " + getPodName();
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
                                startAlive.set(System.nanoTime());
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
                                LOGGER.log(Level.FINEST, "onClose : {0} [{1} ms]", new Object[]{finished, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startAlive.get())});
                                if (finished.getCount() == 0) {
                                    LOGGER.log(Level.WARNING,
                                            "onClose called but latch already finished. This indicates a bug in the kubernetes-plugin");
                                }
                                finished.countDown();
                            }
                        });

                ExecWatch watch;
                try {
                    watch = execable.exec(sh);
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
                    // Depends on the ping time with the Kubernetes API server
                    // Not fully satisfied with this solution because it can delay the execution
                    if (finished.await(COMMAND_FINISHED_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        launcher.getListener().error("Process exited immediately after creation. See output below%n%s", stdout.toString(StandardCharsets.UTF_8.name()));
                        throw new AbortException("Process exited immediately after creation. Check logs above for more details.");
                    }
                    toggleStdout.disable();
                    OutputStream stdin = watch.getInput();
                    PrintStream in = new PrintStream(stdin, true, StandardCharsets.UTF_8.name());
                    if (pwd != null) {
                        // We need to get into the project workspace.
                        // The workspace is not known in advance, so we have to execute a cd command.
                        in.println(String.format("cd \"%s\"", pwd));
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

                    setupEnvironmentVariable(envVars, in, sh.equals("cmd"));

                    doExec(in, printStream, masks, commands);

                    LOGGER.log(Level.INFO, "Created process inside pod: [" + getPodName() + "], container: ["
                            + containerName + "]" + "[" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startMethod) + " ms]");
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

            private void setupEnvironmentVariable(EnvVars vars, PrintStream out, boolean windows) throws IOException {
                for (Map.Entry<String, String> entry : vars.entrySet()) {
                    //Check that key is bash compliant.
                    if (entry.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                            out.println(
                                    String.format(
                                            windows ? "set %s=%s" : "export %s='%s'",
                                            entry.getKey(),
                                            windows ? entry.getValue() : entry.getValue().replace("'", "'\\''")
                                    )
                            );
                        }
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
                LOGGER.log(Level.FINE, "failed to close", e);
            }
        }
    }

    private static class ToggleOutputStream extends FilterOutputStream {
        private boolean disabled;
        public ToggleOutputStream(OutputStream out) {
            super(out);
        }

        public void disable() {
            disabled = true;
        }

        public void enable() {
            disabled = false;
        }

        @Override
        public void write(int b) throws IOException {
            if (!disabled) {
                out.write(b);
            }
        }
    }

    /**
     * Process given stream and mask as specified by the bitfield.
     * Uses space as a separator to determine which fragments to hide.
     */
    private static class MaskOutputStream extends FilterOutputStream {
        private static final String MASK_STRING = "********";

        private final boolean[] masks;
        private final static char SEPARATOR = ' ';
        private int index;
        private boolean wrote;


        public MaskOutputStream(OutputStream out, boolean[] masks) {
            super(out);
            this.masks = masks;
        }

        @Override
        public void write(int b) throws IOException {
            if (masks == null || index >= masks.length) {
                out.write(b);
            } else if (isSeparator(b)) {
                out.write(b);
                index++;
                wrote = false;
            } else if (masks[index]) {
                if (!wrote) {
                    wrote = true;
                    for (char c : MASK_STRING.toCharArray()) {
                        out.write(c);
                    }
                }
            } else {
                out.write(b);
            }
        }

        private boolean isSeparator(int b) {
            return b == SEPARATOR;
        }
    }

    private static void doExec(PrintStream in, PrintStream out, boolean[] masks, String... statements) {
        long start = System.nanoTime();
        // For logging
        ByteArrayOutputStream loggingOutput = new ByteArrayOutputStream();
        // Tee both outputs
        TeeOutputStream teeOutput = new TeeOutputStream(out, loggingOutput);
        // Mask sensitive output
        MaskOutputStream maskedOutput = new MaskOutputStream(teeOutput, masks);
        // Tee everything together
        PrintStream tee = null;
        try {
            String encoding = StandardCharsets.UTF_8.name();
            tee = new PrintStream(new TeeOutputStream(in, maskedOutput), false, encoding);
            // To output things that shouldn't be considered for masking
            PrintStream unmasked = new PrintStream(teeOutput, false, encoding);
            unmasked.print("Executing command: ");
            for (int i = 0; i < statements.length; i++) {
                tee.append("\"")
                   .append(statements[i])
                   .append("\" ");
            }
            tee.println();
            LOGGER.log(Level.FINEST, loggingOutput.toString(encoding) + "[" + TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start) + " μs." + "]");
            // We need to exit so that we know when the command has finished.
            tee.println(EXIT);
            tee.flush();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    static String[] getCommands(Launcher.ProcStarter starter, String containerWorkingDirStr) {
        List<String> allCommands = new ArrayList<String>();

        // BourneShellScript.launchWithCookie escapes $ as $$, we convert it to \$
        for (String cmd : starter.cmds()) {
            String fixedCommand = cmd.replaceAll("\\$\\$", Matcher.quoteReplacement("\\$"))
                    .replaceAll("\\\"", Matcher.quoteReplacement("\\\""));

            String oldRemoteDir = null;
            FilePath oldRemoteDirFilepath = starter.pwd();
            if (oldRemoteDirFilepath != null) {
                oldRemoteDir = oldRemoteDirFilepath.getRemote();
            }
            if (oldRemoteDir != null && ! oldRemoteDir.isEmpty() &&
                    !oldRemoteDir.equals(containerWorkingDirStr) && fixedCommand.contains(oldRemoteDir)) {
                // Container has a custom workingDir, update the dir in commands
                fixedCommand = fixedCommand.replaceAll(oldRemoteDir, containerWorkingDirStr);
            }
            allCommands.add(fixedCommand);
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

    private static String[] fixDoubleDollar(String[] envVars) {
        return Arrays.stream(envVars)
                .map(ev -> ev.replaceAll("\\$\\$", Matcher.quoteReplacement("$")))
                .toArray(String[]::new);
    }
}
