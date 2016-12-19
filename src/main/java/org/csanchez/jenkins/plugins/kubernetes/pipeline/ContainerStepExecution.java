package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.AbortException;
import hudson.FilePath;
import hudson.LauncherDecorator;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ContainerStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 7634132798345235774L;

    private static final transient Logger LOGGER = Logger.getLogger(ContainerStepExecution.class.getName());
    private static final transient String HOSTNAME_FILE = "/etc/hostname";

    @Inject
    private ContainerStep step;
    @StepContextParameter private transient FilePath workspace;
    @StepContextParameter private transient TaskListener listener;

    private transient KubernetesClient client;
    private transient ContainerExecDecorator decorator;

    @Override
    public boolean start() throws Exception {
        LOGGER.log(Level.FINE, "Starting container step.");
        StepContext context = getContext();
        String podName = workspace.child(HOSTNAME_FILE).readToString().trim();
        String containerName = step.getName();

        final AtomicBoolean podAlive = new AtomicBoolean(false);
        final CountDownLatch podStarted = new CountDownLatch(1);
        final CountDownLatch podFinished = new CountDownLatch(1);

        KubernetesCloud cloud = (KubernetesCloud) Jenkins.getInstance().getCloud(step.getCloud());
        if (cloud == null) {
            throw new AbortException(String.format("Cloud does not exist: %s", step.getCloud()));
        }
        client = cloud.connect();

        decorator = new ContainerExecDecorator(client, podName, containerName, workspace.getRemote(), podAlive, podStarted, podFinished);
        context.newBodyInvoker()
                .withContext(BodyInvoker
                        .mergeLauncherDecorators(getContext().get(LauncherDecorator.class), decorator))
                .withCallback(new ContainerExecCallback())
                .start();
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        LOGGER.log(Level.FINE, "Stopping container step.");
        closeQuietly(client, decorator);
    }

    private void closeQuietly(Closeable...  closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    listener.error("Error while closing: [" + c + "]");
                }
            }
        }
    }

    private static class ContainerExecCallback extends BodyExecutionCallback {

        @Override
        public void onSuccess(StepContext context, Object result) {
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            context.onFailure(t);
        }
    }
}
