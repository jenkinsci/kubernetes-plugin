package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.AbortException;
import hudson.FilePath;
import hudson.LauncherDecorator;
import hudson.model.Node;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;

public class ContainerStepExecution extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 7634132798345235774L;

    private static final transient Logger LOGGER = Logger.getLogger(ContainerStepExecution.class.getName());
    private static final transient String HOSTNAME_FILE = "/etc/hostname";

    private final ContainerStep step;

    private transient KubernetesClient client;
    private transient ContainerExecDecorator decorator;

    ContainerStepExecution(ContainerStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    public boolean start() throws Exception {
        LOGGER.log(Level.FINE, "Starting container step.");
        FilePath workspace = getContext().get(FilePath.class);
        String podName = workspace.child(HOSTNAME_FILE).readToString().trim();
        String namespace = workspace.child(Config.KUBERNETES_NAMESPACE_PATH).readToString().trim();

        String containerName = step.getName();

        Node node = getContext().get(Node.class);
        if (! (node instanceof KubernetesSlave)) {
            throw new AbortException(String.format("Node is not a Kubernetes node: %s", node.getNodeName()));
        }
        KubernetesSlave slave = (KubernetesSlave) node;
        KubernetesCloud cloud = (KubernetesCloud) slave.getCloud();
        if (cloud == null) {
            throw new AbortException(String.format("Cloud does not exist: %s", slave.getCloudName()));
        }
        client = cloud.connect();

        decorator = new ContainerExecDecorator(client, podName,  containerName, namespace);
        getContext().newBodyInvoker()
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

    private void closeQuietly(Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    try {
                        getContext().get(TaskListener.class).error("Error while closing: [" + c + "]");
                    } catch (IOException | InterruptedException e1) {
                        LOGGER.log(Level.WARNING, "Error writing to task listener", e);
                    }
                }
            }
        }
    }

    private static class ContainerExecCallback extends BodyExecutionCallback {

        private static final long serialVersionUID = 6385838254761750483L;

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
