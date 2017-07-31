package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.Cloud;
import hudson.slaves.CloudSlaveRetentionStrategy;
import hudson.util.TimeUnit2;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class SelfRegisteredSlaveRetentionStrategy extends CloudSlaveRetentionStrategy {

    private static final Logger LOGGER = Logger.getLogger(SelfRegisteredSlaveRetentionStrategy.class.getName());

    private final String cloudName;

    private final String namespace;

    private final String podId;

    private final int maxMinutesIdle;

    public SelfRegisteredSlaveRetentionStrategy(String cloudName, String namespace, String podId, int maxMinutesIdle) {
        Preconditions.checkArgument(isNotBlank(cloudName), "Cloud name needs to be defined");
        this.cloudName = cloudName;
        this.namespace = namespace;
        this.podId = podId;
        this.maxMinutesIdle = maxMinutesIdle;
    }

    @Override
    protected long getIdleMaxTime() {
        return TimeUnit2.MINUTES.toMillis(maxMinutesIdle);
    }

    @Override
    protected long checkCycle() {
        return 1;
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getNamespace() {
        return namespace;
    }

    @Override
    public void kill(Node node) throws IOException {
        LOGGER.info(format("Node %s will be killed now", node.getNodeDescription()));
        Slave slave = (Slave) node;
        if (canTerminate(slave)) {
            terminate(slave);
        }
    }

    @VisibleForTesting
    boolean canTerminate(Slave slave) {
        String slaveNodeName = slave.getNodeName();
        try {
            // Double-checking if it's the same slave - otherwise the retention strategy shouldn't apply
            KubernetesSlaveUtils.checkSlaveName(slaveNodeName, podId);

            LOGGER.log(Level.FINE, "Checking if can terminate Kubernetes instance for slave {0}", slaveNodeName);
            checkSlaveComputer(slave);
            checkCloudExistence();
            return true;
        } catch (CloudEntityVerificationException e) {
            return false;
        }
    }

    @VisibleForTesting
    void checkSlaveComputer(Slave slave) {
        KubernetesSlaveUtils.checkSlaveComputer(slave);
    }

    @VisibleForTesting
    void checkCloudExistence() {
        Cloud cloud = getCloud();
        KubernetesCloudUtils.checkCloudExistence(cloud, cloudName);
    }

    private void terminate(Slave slave) throws IOException {
        new SlaveTerminator(getCloud()).terminatePodSlave(slave, namespace);
    }

    @VisibleForTesting
    KubernetesCloud getCloud() {
        return KubernetesCloudUtils.getCloud(cloudName);
    }

}
