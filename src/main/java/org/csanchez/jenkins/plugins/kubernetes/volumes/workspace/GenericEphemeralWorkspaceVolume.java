package org.csanchez.jenkins.plugins.kubernetes.volumes.workspace;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PVCVolumeUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.util.Map;

/**
 * Uses a generic ephemeral volume, that is created before the agent pod is created, and terminated afterwards.
 */
@SuppressFBWarnings(
        value = "SE_NO_SERIALVERSIONID",
        justification = "Serialization happens exclusively through XStream and not Java Serialization.")
public class GenericEphemeralWorkspaceVolume extends WorkspaceVolume {

    private String storageClassName;
    private String requestsSize;
    private String accessModes;

    @DataBoundConstructor
    public GenericEphemeralWorkspaceVolume(String storageClassName, String requestsSize, String accessModes) {
        this.storageClassName = storageClassName;
        this.requestsSize = requestsSize;
        this.accessModes = accessModes;
    }

    public String getStorageClassName() {
        return storageClassName;
    }

    public void setStorageClassName(String storageClassName) {
        this.storageClassName = storageClassName;
    }

    public String getRequestsSize() {
        return requestsSize;
    }

    public void setRequestsSize(@CheckForNull String requestsSize) {
        this.requestsSize = Util.fixEmpty(requestsSize);
    }

    public String getAccessModes() {
        return accessModes;
    }

    public void setAccessModes(String accessModes) {
        this.accessModes = accessModes;
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewEphemeral()
                .withNewVolumeClaimTemplate()
                .withNewSpec()
                .withAccessModes(getAccessModes())
                .withStorageClassName(getStorageClassName())
                .withNewResources()
                .withRequests(Map.of("storage", new Quantity(getRequestsSize())))
                .endResources()
                .endSpec()
                .endVolumeClaimTemplate()
                .endEphemeral()
                .build();
    }

    @Extension
    @Symbol("genericEphemeralVolume")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {
        @Override
        public String getDisplayName() {
            return "Generic Ephemerel Volume";
        }

        @SuppressWarnings("unused") // by stapler
        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems() {
            return PVCVolumeUtils.ACCESS_MODES_BOX;
        }
    }
}
