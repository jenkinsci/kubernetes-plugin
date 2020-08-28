package io.jenkins.plugins.kubernetes;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Implementation of {@link NodeProvisioner.Strategy} which will provision a new node immediately as
 * a task enter the queue.
 * In kubernetes, we don't really need to wait before provisioning a new node,
 * because kubernetes agents can be started and destroyed quickly
 *
 * @author <a href="mailto:root@junwuhui.cn">runzexia</a>
 */
@Extension(ordinal = 100)
public class NoDelayProvisionerStrategy extends NodeProvisioner.Strategy {

    private static final Logger LOGGER = Logger.getLogger(NoDelayProvisionerStrategy.class.getName());
    private static final boolean DISABLE_NO_DELAY_PROVISIONING = Boolean.parseBoolean(
            System.getProperty("io.jenkins.plugins.kubernetes.disableNoDelayProvisioning"));

    @Override
    public NodeProvisioner.StrategyDecision apply(NodeProvisioner.StrategyState strategyState) {
        if (DISABLE_NO_DELAY_PROVISIONING) {
            LOGGER.log(Level.FINE, "Provisioning not complete, NoDelayProvisionerStrategy is disabled");
            return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
        }

        final Label label = strategyState.getLabel();

        LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
        int availableCapacity =
                snapshot.getAvailableExecutors()   // live executors
                        + snapshot.getConnectingExecutors()  // executors present but not yet connected
                        + strategyState.getPlannedCapacitySnapshot()     // capacity added by previous strategies from previous rounds
                        + strategyState.getAdditionalPlannedCapacity();  // capacity added by previous strategies _this round_
        int currentDemand = snapshot.getQueueLength();
        LOGGER.log(Level.FINE, "Available capacity={0}, currentDemand={1}",
                new Object[]{availableCapacity, currentDemand});
        int totalPlannedNodes = 0;
        boolean canProvision = false;
        if (availableCapacity < currentDemand) {
            List<Cloud> jenkinsClouds = new ArrayList<>(Jenkins.get().clouds);
            Collections.shuffle(jenkinsClouds);
            for (Cloud cloud : jenkinsClouds) {
                int workloadToProvision = currentDemand - availableCapacity;
                if (!(cloud instanceof KubernetesCloud)) continue;
                if (!cloud.canProvision(label)) continue;
                for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
                    if (cl.canProvision(cloud, strategyState.getLabel(), workloadToProvision) != null) {
                        continue;
                    }
                }
                canProvision = true;
                Collection<NodeProvisioner.PlannedNode> plannedNodes = cloud.provision(label, workloadToProvision);
                LOGGER.log(Level.FINE, "Planned {0} new nodes", plannedNodes.size());
                fireOnStarted(cloud, strategyState.getLabel(), plannedNodes);
                strategyState.recordPendingLaunches(plannedNodes);
                availableCapacity += plannedNodes.size();
                totalPlannedNodes += plannedNodes.size();
                LOGGER.log(Level.FINE, "After provisioning, available capacity={0}, currentDemand={1}", new Object[]{availableCapacity, currentDemand});
                break;
            }
        }
        if (currentDemand - availableCapacity <= 0) {
            LOGGER.log(Level.FINE, String.format("Provisioning completed for label: [%s]", label));
        } else {
            if (!canProvision) {
                return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
            }
            if (totalPlannedNodes > 0 && label != null) {
                LOGGER.log(Level.FINE, "Suggesting NodeProvisioner review");
                Timer.get().schedule(label.nodeProvisioner::suggestReviewNow, 1L, TimeUnit.SECONDS);
            }
        }
        return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
    }

    private static void fireOnStarted(final Cloud cloud, final Label label,
                                      final Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onStarted(cloud, label, plannedNodes);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onStarted() listener call in " + cl + " for label "
                        + label.toString(), e);
            }
        }
    }
}
