package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.annotations.VisibleForTesting;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudSlaveRetentionStrategy;
import hudson.slaves.OfflineCause;
import hudson.util.TimeUnit2;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 */
public class SelfRegisteredSlaveRetentionStrategy extends CloudSlaveRetentionStrategy {

    private static final Logger LOGGER = Logger.getLogger(SelfRegisteredSlaveRetentionStrategy.class.getName());

    /**
     * The resource bundle reference
     */
    private final static ResourceBundleHolder HOLDER = ResourceBundleHolder.get(Messages.class);

    private final String cloudName;
    private final String namespace;
    private final int maxMinutesIdle;

    public SelfRegisteredSlaveRetentionStrategy(String cloudName, String namespace, int maxMinutesIdle) {
        this.cloudName = cloudName;
        this.namespace = namespace;
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

    @Override
    protected void kill(Node n) throws IOException {
        LOGGER.info("Node " + n.getNodeDescription() + " will be killed now");
        terminate(n);
    }

    private void terminate(Node n) throws IOException {
        String name = n.getNodeName();
        LOGGER.log(Level.INFO, "Terminating Kubernetes instance for slave {0}", name);

        Computer computer = n.toComputer();
        if (computer == null) {
            LOGGER.log(Level.SEVERE, "Computer for slave is null: {0}", name);
            return;
        }

        if (cloudName == null) {
            LOGGER.log(Level.SEVERE, "Cloud name is not set for slave, can't terminate: {0}", name);
        }

        try {
            Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
            if (!(cloud instanceof KubernetesCloud)) {
                LOGGER.log(Level.SEVERE, "Slave cloud is not a KubernetesCloud, something is very wrong: {0}", name);
            }
            KubernetesClient client = ((KubernetesCloud) cloud).connect();
            deletePod(name, client);
            computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
            LOGGER.log(Level.INFO, "Disconnected computer {0}", name);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to terminate pod for slave " + name, e);
        }
    }

    private void deletePod(String name, KubernetesClient client) {
        String podName = getPodName(name);
        PodResource<Pod, DoneablePod> pods = client.pods().inNamespace(namespace).withName(podName);
        Boolean deletionResult = pods.delete();
        if (deletionResult == null) {
            LOGGER.log(Level.SEVERE, "Pod {0} was not found in namespace {1}", new Object[] {podName, namespace});
        } else if (!deletionResult) {
            LOGGER.log(Level.SEVERE, "Failed to delete pod {0 from namespace {1}", new Object[] {podName, namespace});
        } else {
            LOGGER.log(Level.INFO, "Terminated Kubernetes instance for slave {0}", name);
        }
    }

    @VisibleForTesting
    static String getPodName(String nodeName) {
        if (StringUtils.countMatches(nodeName, "-") <=1 ) {
            return nodeName;
        }
        // TODO: self-registered slaves should also have the name == pod name
        // As per contract for self-registered slaves, names should at least begin with pod name
        return nodeName.substring(0, nodeName.lastIndexOf('-'));
    }

}
