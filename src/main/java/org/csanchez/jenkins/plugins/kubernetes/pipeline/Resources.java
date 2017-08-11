package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Resources {
    private static final transient Logger LOGGER = Logger.getLogger(ContainerStepExecution.class.getName());
    static void closeQuietly(StepContext context, Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    try {
                        context.get(TaskListener.class).error("Error while closing: [" + c + "]");
                    } catch (IOException | InterruptedException e1) {
                        LOGGER.log(Level.WARNING, "Error writing to task listener", e);
                    }
                }
            }
        }
    }
}
