package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.*;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.csanchez.jenkins.plugins.kubernetes.pipeline.Resources.closeQuietly;

public class ContainerLogStepExecution extends SynchronousNonBlockingStepExecution<String> {
    private static final transient Logger LOGGER = Logger.getLogger(ContainerLogStepExecution.class.getName());

    private final ContainerLogStep step;
    private transient KubernetesClient client;

    ContainerLogStepExecution(ContainerLogStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    private PrintStream logger() {
        TaskListener l = null;
        StepContext context = getContext();
        try {
            l = context.get(TaskListener.class);
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, "Failed to find TaskListener in context");
        } finally {
            if (l == null) {
                l = new LogTaskListener(LOGGER, Level.FINE);
            }
        }
        return l.getLogger();
    }

    @Override
    protected String run() throws Exception {
        boolean returnLog = step.isReturnLog();
        String containerName = step.getName();
        int tailingLines = step.getTailingLines();
        int sinceSeconds = step.getSinceSeconds();
        int limitBytes = step.getLimitBytes();

        try {
            LOGGER.log(Level.FINE, "Starting containerLog step.");

            KubernetesNodeContext nodeContext = new KubernetesNodeContext(getContext());
            client = nodeContext.connectToCloud();

            String podName = nodeContext.getPodName();
            ContainerResource<String, LogWatch, InputStream, PipedOutputStream, OutputStream, PipedInputStream,
                    String, ExecWatch> container = client.pods()
                    .inNamespace(nodeContext.getNamespace())
                    .withName(podName)
                    .inContainer(containerName);

            TimeTailPrettyLoggable<String, LogWatch> limited = limitBytes > 0 ? container.limitBytes(limitBytes) : container;

            TailPrettyLoggable<String, LogWatch> since = sinceSeconds > 0 ? limited.sinceSeconds(sinceSeconds) : limited;

            PrettyLoggable<String, LogWatch> tailed = tailingLines > 0 ? since.tailingLines(tailingLines) : since;
            String log = tailed.getLog();

            if (returnLog) {
                return log;
            } else {
                logger().println("> start log of container '" + containerName + "' in pod '" + podName + "'");
                logger().print(log);
                if (log.length() > 0 && log.charAt(log.length() - 1) != '\n') {
                    logger().println();
                }
                logger().println("> end log of container '" + containerName + "' in pod '" + podName + "'");
            }

            return "";
        } catch (InterruptedException e) {
            logger().println("Interrupted while getting logs of container");
            LOGGER.log(Level.FINE, "interrupted while getting logs of container {1}", containerName);
            return "";
        } catch (Exception e) {
            String message = "Failed to get logs for container";
            logger().println(message);
            LOGGER.log(Level.WARNING, message, e);
            return "";
        } finally {
            closeQuietly(getContext(), client);
        }
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        LOGGER.log(Level.FINE, "Stopping container log step.");
        try {
            super.stop(cause);
        } finally {
            closeQuietly(getContext(), client);
        }
    }
}
