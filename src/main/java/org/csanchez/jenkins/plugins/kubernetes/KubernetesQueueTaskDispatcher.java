package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;

import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import jenkins.model.Jenkins;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import java.util.HashSet;
import java.util.Set;

@Extension
@SuppressWarnings({"rawtypes"})
public class KubernetesQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canTake(Node node, Queue.Task item) {
        if (node instanceof KubernetesSlave) {
            KubernetesSlave slave = (KubernetesSlave) node;
            Task ownerTask = item.getOwnerTask();
            if (ownerTask instanceof WorkflowJob) {
                WorkflowJob workflowJob = (WorkflowJob) ownerTask;
                return check(slave, workflowJob);
            }
            if (item instanceof Job) {
                return check(slave, (Job) item);
            }
        }
        return null;
    }

    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
        return this.canTake(node, item.task);
    }

    private CauseOfBlockage check(KubernetesSlave agent, Job job) {
        ItemGroup parent = job.getParent();
        Set<String> allowedClouds = new HashSet<>();

        KubernetesCloud targetCloud = Jenkins.get().clouds.getAll(KubernetesCloud.class)
                .stream()
                .filter(cloud -> cloud.name.equals(agent.getCloudName()))
                .findAny()
                .orElse(null);
        if (targetCloud != null && targetCloud.isUsageRestricted()) {
            KubernetesFolderProperty.collectAllowedClouds(allowedClouds, parent);
            if (!allowedClouds.contains(targetCloud.name)) {
                return new CauseOfBlockage.BecauseNodeIsBusy(agent);
            }
        }
        return null;
    }

}
