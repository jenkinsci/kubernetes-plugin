package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ContainerListenDecoratorUnitTest {

    @Test
    public void shouldSkipNonPosixEnvVarsWithoutPrintingToBuildLog(@TempDir File tempDir) throws Exception {
        // Setup temporary directory path for workspace volume mount
        String tempWorkspacePath = tempDir.getAbsolutePath().replace('\\', '/');

        // Build Pod spec
        Container agentContainer = new ContainerBuilder()
                .withName("jnlp")
                .addNewEnv()
                .withName("JENKINS_AGENT_WORKDIR")
                .withValue(tempWorkspacePath)
                .endEnv()
                .addNewVolumeMount()
                .withName(PodTemplateBuilder.WORKSPACE_VOLUME_NAME)
                .withMountPath(tempWorkspacePath)
                .endVolumeMount()
                .build();
        Container appContainer = new ContainerBuilder()
                .withName("app")
                .addNewEnv()
                .withName("JENKINS_CONTAINER_NAME")
                .withValue("app")
                .endEnv()
                .addNewVolumeMount()
                .withName(PodTemplateBuilder.WORKSPACE_VOLUME_NAME)
                .withMountPath(tempWorkspacePath)
                .endVolumeMount()
                .build();
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("test-pod")
                .endMetadata()
                .withNewSpec()
                .withContainers(agentContainer, appContainer)
                .endSpec()
                .build();

        // Mock KubernetesSlave and its computer/environment to avoid NPEs
        KubernetesSlave ks = mock(KubernetesSlave.class);
        when(ks.getPodName()).thenReturn("test-pod");
        when(ks.getPod()).thenReturn(java.util.Optional.of(pod));
        KubernetesComputer computer = mock(KubernetesComputer.class);
        when(ks.toComputer()).thenReturn(computer);
        when(computer.getEnvironment()).thenReturn(new EnvVars());

        // Capture build console log
        ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
        StreamTaskListener listener = new StreamTaskListener(logOutput, StandardCharsets.UTF_8);

        // Mock base Launcher
        Launcher baseLauncher = mock(Launcher.class);
        when(baseLauncher.isUnix()).thenReturn(true);
        when(baseLauncher.getChannel()).thenReturn(null);
        when(baseLauncher.getListener()).thenReturn(listener);
        Launcher dummyLauncher = new Launcher.DummyLauncher(listener);
        Launcher.ProcStarter realStarter = dummyLauncher.launch();
        when(baseLauncher.launch()).thenReturn(realStarter);

        // Instantiate ContainerListenDecorator
        ContainerExecDecorator fallback = mock(ContainerExecDecorator.class);
        ContainerListenDecorator decorator = new ContainerListenDecorator("app", "sh", fallback);
        Launcher decorated = decorator.decorate(baseLauncher, ks);
        assertNotNull(decorated);

        // Prepare ProcStarter with compliant and non-compliant env vars
        Launcher.ProcStarter starter = baseLauncher
                .launch()
                .cmds("echo", "hello")
                .envs(
                        "VALID_VAR=hello",
                        "ANOTHER_VALID=123",
                        "INVALID.VAR=world",
                        "library.my-lib.version=1.0.0",
                        "1INVALID=first_char_digit",
                        "VAR-NAME=hyphen",
                        "SPACE VAR=space")
                .pwd(new FilePath(tempDir))
                .quiet(true); // avoids calling printCommandLine which depends on Launcher.listener field

        // Capture Java Logger output
        Logger decoratorLogger = Logger.getLogger(ContainerListenDecorator.class.getName());
        Level oldLevel = decoratorLogger.getLevel();
        decoratorLogger.setLevel(Level.FINE);
        java.util.List<LogRecord> loggedRecords = new java.util.ArrayList<>();
        Handler testHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                loggedRecords.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        decoratorLogger.addHandler(testHandler);

        try {
            // Launch the process (writes configuration files)
            hudson.Proc proc = decorated.launch(starter);
            assertNotNull(proc);

            // Locate script.sh recursively
            File script = findScriptSh(tempDir);
            assertNotNull(script, "script.sh should be generated");

            String scriptContent = new FilePath(script).readToString();

            // Verify valid env vars are exported
            assertTrue(scriptContent.contains("export 'VALID_VAR=hello'"));
            assertTrue(scriptContent.contains("export 'ANOTHER_VALID=123'"));

            // Verify all types of invalid env vars are skipped
            assertFalse(scriptContent.contains("INVALID.VAR"));
            assertFalse(scriptContent.contains("library.my-lib.version"));
            assertFalse(scriptContent.contains("1INVALID"));
            assertFalse(scriptContent.contains("VAR-NAME"));
            assertFalse(scriptContent.contains("SPACE VAR"));

            // Verify build console output log remains clean of warnings
            String consoleLogs = logOutput.toString(StandardCharsets.UTF_8);
            assertFalse(
                    consoleLogs.contains("Ignoring unsupported env var"),
                    "Build console log should not contain warnings for unsupported env vars");

            // Verify warnings are logged at Level.FINE in Java Logger
            assertTrue(loggedRecords.stream()
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage() != null
                            && r.getMessage().contains("Ignoring unsupported env var INVALID.VAR")));
            assertTrue(loggedRecords.stream()
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage() != null
                            && r.getMessage().contains("Ignoring unsupported env var library.my-lib.version")));
            assertTrue(loggedRecords.stream()
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage() != null
                            && r.getMessage().contains("Ignoring unsupported env var 1INVALID")));
            assertTrue(loggedRecords.stream()
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage() != null
                            && r.getMessage().contains("Ignoring unsupported env var VAR-NAME")));
            assertTrue(loggedRecords.stream()
                    .anyMatch(r -> r.getLevel() == Level.FINE
                            && r.getMessage() != null
                            && r.getMessage().contains("Ignoring unsupported env var SPACE VAR")));

        } finally {
            decoratorLogger.removeHandler(testHandler);
            decoratorLogger.setLevel(oldLevel);
        }
    }

    private File findScriptSh(File dir) {
        if (dir.getName().equals("script.sh")) {
            return dir;
        }
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    File res = findScriptSh(child);
                    if (res != null) {
                        return res;
                    }
                }
            }
        }
        return null;
    }
}
