package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.RetentionStrategy;
import jenkins.model.NodeListener;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@Extension
public class SelfRegisteredSlaveNodeListener extends NodeListener {

    private static final Logger LOGGER = Logger.getLogger(SelfRegisteredSlaveNodeListener.class.getName());

    @Override
    protected void onDeleted(@Nonnull Node node) {
        LOGGER.log(Level.INFO, "Node {0} was deleted", node);
        String labelString = node.getLabelString();
        LOGGER.log(Level.INFO, "Node labels: {0}", labelString);

        if (node instanceof AbstractCloudSlave) {
            AbstractCloudSlave slave = (AbstractCloudSlave) node;
            RetentionStrategy retentionStrategy = slave.getRetentionStrategy();
            if (retentionStrategy != null && retentionStrategy instanceof SelfRegisteredSlaveRetentionStrategy) {
                String podName = node.getNodeName();
                LOGGER.log(Level.INFO, "Going to delete pod {0}", podName);
                try {
                    // Let's reuse retention strategy, since it's already capable of deleting pods.
                    kill((SelfRegisteredSlaveRetentionStrategy) retentionStrategy, node);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, format("Failed to delete pod %s", podName), e);
                }
            }
        }
    }

    @VisibleForTesting
    void kill(SelfRegisteredSlaveRetentionStrategy retentionStrategy, @Nonnull Node node) throws IOException {
        retentionStrategy.kill(node);
    }

}
