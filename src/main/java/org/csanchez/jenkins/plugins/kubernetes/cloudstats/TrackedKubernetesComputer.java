package org.csanchez.jenkins.plugins.kubernetes.cloudstats;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.cloudstats.CloudStatistics;
import org.jenkinsci.plugins.cloudstats.ProvisioningActivity;
import org.jenkinsci.plugins.cloudstats.TrackedItem;

/**
 * A {@link KubernetesComputer} that Cloud Statistics can correlate with its provisioning activity
 * (JENKINS-67256). Used only when the cloud-stats plugin is installed; supplied by
 * {@link TrackedKubernetesComputerFactory}.
 */
public class TrackedKubernetesComputer extends KubernetesComputer implements TrackedItem {

    public TrackedKubernetesComputer(KubernetesSlave slave) {
        super(slave);
    }

    /**
     * Resolves the tracking identity by node name from the recorded activities rather than storing it
     * on the agent. This keeps the always-loaded core free of cloud-stats types and is restart-safe:
     * cloud-stats persists its activities, so after a controller restart the matching activity is still
     * found. Returns {@code null} for a legacy or untracked agent that has no activity, in which case
     * cloud-stats quietly does nothing.
     */
    @Override
    @CheckForNull
    public ProvisioningActivity.Id getId() {
        String nodeName = getName();
        if (nodeName == null) {
            return null;
        }
        for (ProvisioningActivity activity : CloudStatistics.get().getActivities()) {
            ProvisioningActivity.Id id = activity.getId();
            if (nodeName.equals(id.getNodeName())) {
                return id;
            }
        }
        return null;
    }
}
