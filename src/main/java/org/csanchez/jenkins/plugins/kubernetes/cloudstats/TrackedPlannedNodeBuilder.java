package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.NodeProvisioner;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PlannedNodeBuilder;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;

/**
 * A {@link PlannedNodeBuilder} that returns a Cloud Statistics {@link TrackedPlannedNode}, so
 * provisioning a Kubernetes agent opens a {@code PROVISIONING} activity (JENKINS-67256). Used only
 * when the cloud-stats plugin is installed; registered through {@link TrackedPlannedNodeBuilderFactory}.
 */
public class TrackedPlannedNodeBuilder extends PlannedNodeBuilder {
    @Override
    public NodeProvisioner.PlannedNode build() {
        CompletableFuture<Node> f;
        ProvisioningActivity.Id id;
        try {
            KubernetesSlave agent = buildAgent();
            // Mint the identity from the resolved agent's node name so the planned node, the recorded
            // activity, and the agent's computer (which resolves its Id by node name) all correlate.
            id = new ProvisioningActivity.Id(getCloud().name, getTemplate().getName(), agent.getNodeName());
            f = CompletableFuture.completedFuture(agent);
        } catch (IOException | Descriptor.FormException e) {
            // No agent was resolved, so there is no node name to key on: track the failed attempt by
            // cloud and template only.
            id = new ProvisioningActivity.Id(getCloud().name, getTemplate().getName());
            f = CompletableFuture.failedFuture(e);
        }
        return new TrackedPlannedNode(id, getNumExecutors(), f);
    }
}
