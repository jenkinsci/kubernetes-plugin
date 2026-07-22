package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.NodeProvisioner;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * The default {@link PlannedNodeBuilder} implementation, in case there is other registered.
 */
public class StandardPlannedNodeBuilder extends PlannedNodeBuilder {
    @Override
    public NodeProvisioner.PlannedNode build() {
        CompletableFuture<Node> f;
        String displayName;
        try {
            KubernetesSlave agent = buildAgent();
            displayName = agent.getDisplayName();
            f = CompletableFuture.completedFuture(agent);
        } catch (IOException | Descriptor.FormException e) {
            displayName = null;
            f = CompletableFuture.failedFuture(e);
        }
        return new NodeProvisioner.PlannedNode(Util.fixNull(displayName), f, getNumExecutors());
    }
}
