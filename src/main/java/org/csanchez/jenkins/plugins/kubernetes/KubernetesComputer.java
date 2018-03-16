package org.csanchez.jenkins.plugins.kubernetes;

import hudson.EnvVars;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import java.io.IOException;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesComputer extends AbstractCloudComputer<KubernetesSlave> {
    private static final Logger LOGGER = Logger.getLogger(KubernetesComputer.class.getName());

    public KubernetesComputer(KubernetesSlave slave) {
        super(slave);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        LOGGER.fine(" Computer " + this + " taskAccepted");
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompleted");

        // May take the agent offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompletedWithProblems");
    }

    @Override
    public String toString() {
        return String.format("KubernetesComputer name: %s slave: %s", getName(), getNode());
    }

    /*
    Return empty EnvVars since we don't want to inject jnlp agent system variables;
    */
    @Override
      public EnvVars getEnvironment() throws IOException, InterruptedException {
        return new EnvVars();
    }



}
