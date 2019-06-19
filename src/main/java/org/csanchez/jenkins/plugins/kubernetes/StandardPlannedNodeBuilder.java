package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.util.concurrent.Futures;
import hudson.model.Descriptor;
import hudson.slaves.NodeProvisioner;

import java.io.IOException;

/**
 * The default {@link PlannedNodeBuilder} implementation, in case there is other registered.
 */
public class StandardPlannedNodeBuilder extends PlannedNodeBuilder {
    @Override
    public NodeProvisioner.PlannedNode build() {
        KubernetesCloud cloud = getCloud();
        PodTemplate t = getTemplate();
        try {
            return new NodeProvisioner.PlannedNode(t.getDisplayName(),
                    Futures.immediateFuture(KubernetesSlave
                            .builder()
                            .podTemplate(cloud.getUnwrappedTemplate(t))
                            .cloud(cloud)
                            .build()),
                    getNumExecutors());
        } catch (IOException | Descriptor.FormException e) {
            throw new RuntimeException(e);
        }
    }
}
