package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Computer;
import hudson.slaves.NodeProvisioner;

/**
 * The default {@link PlannedNodeBuilder} implementation, in case there is other registered.
 */
public class StandardPlannedNodeBuilder extends PlannedNodeBuilder {
    @Override
    public NodeProvisioner.PlannedNode build() {
        return new NodeProvisioner.PlannedNode(getTemplate().getDisplayName(),
                Computer.threadPoolForRemoting.submit(new ProvisioningCallback(getCloud(), getTemplate())),
                getNumExecutors());
    }
}
