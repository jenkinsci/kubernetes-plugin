package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.NodeProvisioner;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedPlannedNode;

/**
 * The default {@link PlannedNodeBuilder} implementation, in case there is other registered.
 */
public class StandardPlannedNodeBuilder extends PlannedNodeBuilder {
    @Override
    public NodeProvisioner.PlannedNode build() {
        KubernetesCloud cloud = getCloud();
        PodTemplate t = getTemplate();
        CompletableFuture<Node> f;
        ProvisioningActivity.Id id = null;
        try {
            KubernetesSlave agent = KubernetesSlave.builder()
                    .podTemplate(t.isUnwrapped() ? t : cloud.getUnwrappedTemplate(t))
                    .cloud(cloud)
                    .build();
            // Reuse the id minted by the agent so the planned node, the agent, and the recorded
            // cloud-stats activity all share one tracking identity (JENKINS-67256).
            id = agent.getId();
            f = CompletableFuture.completedFuture(agent);
        } catch (IOException | Descriptor.FormException e) {
            f = CompletableFuture.failedFuture(e);
        }
        if (id == null) {
            id = new ProvisioningActivity.Id(cloud.name, t.getName());
        }
        return new TrackedPlannedNode(id, getNumExecutors(), f);
    }
}
