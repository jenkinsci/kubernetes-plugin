package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;

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
        annotatePodWithTaskName(task);
        super.taskAccepted(executor, task);
        LOGGER.fine(" Computer " + this + " taskAccepted");
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        cleanupPodAnnotation();
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompleted");

        // May take the slave offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        cleanupPodAnnotation();
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompletedWithProblems");
    }

    @Override
    public String toString() {
        return String.format("KubernetesComputer name: %s slave: %s", getName(), getNode());
    }

    private void annotatePodWithTaskName(Queue.Task task) {
        KubernetesClient k8sClient = null;
        try {
            KubernetesSlave slave = getNode();
            if (slave != null) {
                k8sClient = slave.getKubernetesCloud().connect();
                Pod done = k8sClient.pods().withName(slave.getNodeName()).edit().editMetadata()
                        .addToAnnotations("jenkins.task.name", task.getName()).endMetadata().done();

                String nodeName = done.getSpec().getNodeName();
                LOGGER.info("accepted task [" + task.getName() + "] in pod [" + slave.getNodeName() + "] on node [" + nodeName + "]");
            }
        }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cannot contact k8s server", e);
        }
        finally {
            if (k8sClient != null) {
                k8sClient.close();
            }
        }
    }

    private void cleanupPodAnnotation() {
        KubernetesClient k8sClient = null;
        try {
            KubernetesSlave slave = getNode();
            if (slave != null) {
                k8sClient = slave.getKubernetesCloud().connect();
                k8sClient.pods().withName(slave.getNodeName()).edit().editMetadata()
                        .removeFromAnnotations("jenkins.task.name").endMetadata().done();
            }
        }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "Cannot contact k8s server", e);
        }
        finally {
            if (k8sClient != null) {
                k8sClient.close();
            }
        }
    }
}
