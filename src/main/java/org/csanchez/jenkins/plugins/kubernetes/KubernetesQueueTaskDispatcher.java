package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import jenkins.model.Jenkins;

import java.util.HashSet;
import java.util.Set;

@Extension
@SuppressWarnings({"unused", "rawtypes"})
public class KubernetesQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canTake(Node node, Queue.Task task) {
        if (node instanceof KubernetesSlave) {
            if (task instanceof Job) {
                KubernetesSlave slave = (KubernetesSlave) node;
                Job project = (Job) task;
                ItemGroup parent = project.getParent();
                Set<String> allowedClouds = new HashSet<>();

                KubernetesCloud targetCloud = Jenkins.get().clouds.getAll(KubernetesCloud.class)
                        .stream()
                        .filter(cloud -> cloud.name.equals(slave.getCloudName()))
                        .findAny()
                        .orElse(null);
                if (targetCloud != null && targetCloud.isUsageRestricted()) {
                    KubernetesFolderProperty.collectAllowedClouds(allowedClouds, parent);
                    if (!allowedClouds.contains(targetCloud.name)) {
                        return new CauseOfBlockage.BecauseNodeIsBusy(node);
                    }
                }
            }
        }
        return null;
    }

    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
        return this.canTake(node, item.task);
    }
}
