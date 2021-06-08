package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Util;
import hudson.model.Descriptor;
import hudson.slaves.NodeProvisioner;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * The default {@link PlannedNodeBuilder} implementation, in case there is other registered.
 */
public class StandardPlannedNodeBuilder extends PlannedNodeBuilder {
    @Override
    public NodeProvisioner.PlannedNode build() {
        KubernetesCloud cloud = getCloud();
        PodTemplate t = getTemplate();
        CompletableFuture f;
        String displayName;
        try {
            KubernetesSlave agent = KubernetesSlave
                    .builder()
                    .podTemplate(cloud.getUnwrappedTemplate(t))
                    .cloud(cloud)
                    .build();
            displayName = agent.getDisplayName();
            f = CompletableFuture.completedFuture(agent);
        } catch (IOException | Descriptor.FormException e) {
            displayName = null;
            f = new CompletableFuture();
            f.completeExceptionally(e);
        }
        return new NodeProvisioner.PlannedNode(Util.fixNull(displayName), f, getNumExecutors());
    }
}
