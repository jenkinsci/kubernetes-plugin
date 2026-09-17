package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import hudson.model.Node;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;
import org.jenkinsci.plugins.variant.OptionalExtension;

/**
 * Ensures a cloud-stats {@code PROVISIONING} activity is opened for a Kubernetes agent whose planned
 * node was not cloud-stats-tracked, so provisioning is recorded no matter which
 * {@code PlannedNodeBuilder} produced the node. Registered only when the cloud-stats plugin is installed
 * (JENKINS-67256).
 *
 * <p>The default {@code TrackedPlannedNodeBuilder} returns a {@code TrackedPlannedNode}, so cloud-stats
 * opens the activity itself from its {@code ProvisioningListener.onStarted} and this listener is a
 * no-op. But {@code PlannedNodeBuilderFactory} is an extension point: another plugin — or a Jenkins
 * distribution — can register a factory whose builder returns a plain {@link NodeProvisioner.PlannedNode}
 * that is not a cloud-stats {@link TrackedItem}. cloud-stats then never opens a {@code PROVISIONING}
 * activity, so when the agent connects its {@code OperationListener} finds nothing to advance and logs
 * {@code "No activity tracked for ..."}, and the attempt is invisible in Cloud Statistics.
 *
 * <p>Wrapping the planned node up front is not always possible: a custom builder may create the node
 * asynchronously. Once provisioning completes this listener mints a tracking
 * {@link ProvisioningActivity.Id} from the resolved agent (its cloud, template and node name) and opens
 * the activity through cloud-stats' public {@code ProvisioningListener#onStarted(Id)} seam, the one
 * documented for provisioning that happens outside the tracked-planned-node path. The node name in that
 * Id lets {@link TrackedKubernetesComputer} resolve the same activity when the agent connects, so
 * cloud-stats advances {@code LAUNCHING}/{@code OPERATING}/{@code COMPLETED} as usual and
 * {@link KubernetesLaunchFailureListener} can record a {@code LAUNCHING:FAIL} against it.
 *
 * <p>Scope: this compensates only for a <em>successful</em> provisioning whose planned node was
 * untracked. A pure provisioning <em>failure</em> on that path produces no {@link Node}, hence no node
 * name to key an activity on, so it cannot be recorded here — unlike the default path, where
 * cloud-stats' own {@code ProvisioningListener.onFailure} records {@code PROVISIONING:FAIL}. And because
 * the activity is opened at {@code onComplete} (node already built), its {@code PROVISIONING} phase
 * carries no meaningful duration on this path; the value is that the attempt becomes visible in Cloud
 * Statistics and traverses the remaining phases.
 */
@OptionalExtension(requirePlugins = "cloud-stats")
public class KubernetesProvisioningActivityListener extends CloudProvisioningListener {

    private static final Logger LOGGER = Logger.getLogger(KubernetesProvisioningActivityListener.class.getName());

    @Override
    public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
        if (!(node instanceof KubernetesSlave)) {
            return;
        }
        // A TrackedPlannedNode means cloud-stats already opened the activity from onStarted; opening it
        // again here would create a duplicate. This is the default-builder path, where nothing is needed.
        if (plannedNode instanceof TrackedItem) {
            return;
        }
        KubernetesSlave slave = (KubernetesSlave) node;
        String nodeName = slave.getNodeName();
        // A freshly minted Id has a new fingerprint every time, so dedupe by node name — the stable key
        // TrackedKubernetesComputer also resolves on. Idempotent, and a no-op once the activity exists.
        if (hasActivityForNode(nodeName)) {
            return;
        }
        ProvisioningActivity.Id id = idFor(slave);
        CloudStatistics.ProvisioningListener.get().onStarted(id);
        LOGGER.log(
                Level.FINE,
                () -> "Opened cloud-stats provisioning activity for Kubernetes agent " + nodeName
                        + "; its planned node (" + plannedNode.getClass().getName() + ") was not cloud-stats-tracked");
    }

    private static boolean hasActivityForNode(String nodeName) {
        for (ProvisioningActivity activity : CloudStatistics.get().getActivities()) {
            if (nodeName.equals(activity.getId().getNodeName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mints the tracking Id with the same (cloud name, template name, node name) shape as the default
     * {@code TrackedPlannedNodeBuilder}, but from the resolved agent alone — falling back to the template
     * id when the template cannot be resolved, as the agent's own legacy deserialization did (a fallback
     * the builder itself does not need). Only the node name matters for correlation; the template slot is
     * cosmetic.
     */
    private static ProvisioningActivity.Id idFor(KubernetesSlave slave) {
        PodTemplate template = slave.getTemplateOrNull();
        String templateName = template != null ? template.getName() : slave.getTemplateId();
        return new ProvisioningActivity.Id(slave.getCloudName(), templateName, slave.getNodeName());
    }
}
