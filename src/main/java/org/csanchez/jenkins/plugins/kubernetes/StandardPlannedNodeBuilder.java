package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.util.concurrent.Futures;
import hudson.model.Descriptor;
import hudson.slaves.NodeProvisioner;

import java.io.IOException;
import java.util.concurrent.Future;

/**
 * The default {@link PlannedNodeBuilder} implementation, in case there is other registered.
 */
public class StandardPlannedNodeBuilder extends PlannedNodeBuilder {
    @Override
    public NodeProvisioner.PlannedNode build() {
        KubernetesCloud cloud = getCloud();
        PodTemplate t = getTemplate();
        Future f;
        try {
            KubernetesSlave agent = KubernetesSlave
                    .builder()
                    .podTemplate(cloud.getUnwrappedTemplate(t))
                    .cloud(cloud)
                    .build();
            f = Futures.immediateFuture(agent);
        } catch (IOException | Descriptor.FormException e) {
            f = Futures.immediateFailedFuture(e);
        }
        return new NodeProvisioner.PlannedNode(t.getName(), f, getNumExecutors());
    }
}
