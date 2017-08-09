package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.RetentionStrategy;
import jenkins.model.NodeListener;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Deletes the pod with self-registering slave from Kubernetes.</p>
 * <p>Actioned by retention strategy or manual slave deletion.</p>
 *
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@Extension
public class SelfRegisteredSlaveNodeListener extends NodeListener {

    private static final Logger LOGGER = Logger.getLogger(SelfRegisteredSlaveNodeListener.class.getName());

    @Override
    protected void onDeleted(@Nonnull Node node) {
        if (node instanceof Slave) {
            Slave slave = (Slave) node;
            RetentionStrategy retentionStrategy = slave.getRetentionStrategy();
            if (retentionStrategy != null && retentionStrategy instanceof SelfRegisteredSlaveRetentionStrategy) {
                kill((SelfRegisteredSlaveRetentionStrategy) retentionStrategy, slave);
            }
        }
    }

    @VisibleForTesting
    void kill(SelfRegisteredSlaveRetentionStrategy retentionStrategy, @Nonnull Slave slave) {
        String podName = slave.getNodeName();
        LOGGER.log(Level.FINE, "Going to delete pod {0}", podName);

        String cloudName = retentionStrategy.getCloudName();
        String namespace = retentionStrategy.getNamespace();
        KubernetesCloud cloud = KubernetesCloudUtils.getCloud(cloudName);

        SlaveTerminator slaveTerminator = new SlaveTerminator(cloud);
        slaveTerminator.terminatePodSlave(slave, namespace);
    }

}
