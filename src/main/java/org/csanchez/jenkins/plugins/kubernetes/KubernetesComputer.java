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

	private static final String TASK_NAME_POD_ANNOTATION = "jenkins.task.name";
	private static final String TASK_STATUS_POD_ANNOTATION = "jenkins.task.status";

	public KubernetesComputer(KubernetesSlave slave) {
        super(slave);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        annotatePodWithTaskInfo(task, "accepted");
        super.taskAccepted(executor, task);
        LOGGER.fine(" Computer " + this + " taskAccepted");
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        annotatePodWithTaskInfo(task, "completed");
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompleted");

        // May take the slave offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        annotatePodWithTaskInfo(task, "completedWithProblems");
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompletedWithProblems");
    }

    @Override
    public String toString() {
        return String.format("KubernetesComputer name: %s slave: %s", getName(), getNode());
    }

    private void annotatePodWithTaskInfo(Queue.Task task, String status) {
        KubernetesClient k8sClient = null;
        try {
            KubernetesSlave slave = getNode();
            if (slave != null) {
                k8sClient = slave.getKubernetesCloud().connect();
                Pod pod = k8sClient.pods().withName(slave.getNodeName()).edit()
                        .editMetadata()
                        .addToAnnotations(TASK_NAME_POD_ANNOTATION, task.getName())
                        .addToAnnotations(TASK_STATUS_POD_ANNOTATION, status)
                        .endMetadata().done();

                String nodeName = pod.getSpec().getNodeName();
                LOGGER.info("task [" + task.getName() + "] updated with status [" + status + "] in pod [" + slave.getNodeName() + "] on node ["
                        + nodeName + "]");
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
