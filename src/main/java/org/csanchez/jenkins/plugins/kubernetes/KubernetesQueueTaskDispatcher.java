package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;

import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;

@Extension
@SuppressWarnings({"rawtypes"})
public class KubernetesQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
        if (node instanceof KubernetesSlave) {
            KubernetesSlave slave = (KubernetesSlave) node;
            Task ownerTask = item.task.getOwnerTask();
            if (!KubernetesFolderProperty.isAllowed(slave, (Job) ownerTask)) {
                return new KubernetesCloudNotAllowed();
            }
        }
        return null;
    }

    public static final class KubernetesCloudNotAllowed extends CauseOfBlockage {
        @Override
        public String getShortDescription() {
            return Messages.KubernetesCloudNotAllowed_Description();
        }
    }
}
