package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Resources.*;

import java.io.Closeable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.LauncherDecorator;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;

public class ContainerStepExecution extends StepExecution {

    private static final long serialVersionUID = 7634132798345235774L;

    private static final transient Logger LOGGER = Logger.getLogger(ContainerStepExecution.class.getName());

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private final transient ContainerStep step;

    private transient KubernetesClient client;
    private ContainerExecDecorator decorator;

    @Override
    // TODO Revisit for JENKINS-40161
    public void onResume() {
        super.onResume();
        LOGGER.log(Level.FINE, "onResume");
        try {
            KubernetesNodeContext nodeContext = new KubernetesNodeContext(getContext());
            client = nodeContext.connectToCloud();
            decorator.setKubernetesClient(client);
        } catch (Exception e) {
            ContainerStepExecution.this.getContext().onFailure(e);
        }
    }

    ContainerStepExecution(ContainerStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    public boolean start() throws Exception {
        LOGGER.log(Level.FINE, "Starting container step.");
        String containerName = step.getName();
        String shell = step.getShell();

        KubernetesNodeContext nodeContext = new KubernetesNodeContext(getContext());
        client = nodeContext.connectToCloud();

        EnvironmentExpander env = getContext().get(EnvironmentExpander.class);
        EnvVars globalVars = null;
        Jenkins instance = Jenkins.getInstance();

        DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = instance.getGlobalNodeProperties();
        List<EnvironmentVariablesNodeProperty> envVarsNodePropertyList = globalNodeProperties
                .getAll(EnvironmentVariablesNodeProperty.class);
        if (envVarsNodePropertyList != null && envVarsNodePropertyList.size() != 0) {
            globalVars = envVarsNodePropertyList.get(0).getEnvVars();
        }

        EnvVars rcEnvVars = null;
        Run run = getContext().get(Run.class);
        TaskListener taskListener = getContext().get(TaskListener.class);
        if(run!=null && taskListener != null) {
            rcEnvVars = run.getEnvironment(taskListener);
        }

        decorator = new ContainerExecDecorator();
        decorator.setClient(client);
        decorator.setPodName(nodeContext.getPodName());
        decorator.setContainerName(containerName);
        decorator.setNamespace(nodeContext.getNamespace());
        decorator.setEnvironmentExpander(env);
        decorator.setWs(getContext().get(FilePath.class));
        decorator.setGlobalVars(globalVars);
        decorator.setRunContextEnvVars(rcEnvVars);
        decorator.setShell(shell);
        getContext().newBodyInvoker()
                .withContext(BodyInvoker
                        .mergeLauncherDecorators(getContext().get(LauncherDecorator.class), decorator))
                .withCallback(new ContainerExecCallback(decorator))
                .start();
        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        LOGGER.log(Level.FINE, "Stopping container step.");
        closeQuietly(getContext(), client, decorator);
    }

    private static class ContainerExecCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 6385838254761750483L;

        private final Closeable[] closeables;

        private ContainerExecCallback(Closeable... closeables) {
            this.closeables = closeables;
        }
        @Override
        public void finished(StepContext context) {
            closeQuietly(context, closeables);
        }
    }

}
