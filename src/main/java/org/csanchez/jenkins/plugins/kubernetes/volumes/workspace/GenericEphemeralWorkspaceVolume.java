package org.csanchez.jenkins.plugins.kubernetes.volumes.workspace;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.Volume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EphemeralVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PVCVolumeUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Uses a generic ephemeral volume, that is created before the agent pod is created, and terminated afterwards.
 */
@SuppressFBWarnings(
        value = "SE_NO_SERIALVERSIONID",
        justification = "Serialization happens exclusively through XStream and not Java Serialization.")
public class GenericEphemeralWorkspaceVolume extends WorkspaceVolume implements EphemeralVolume {

    private String storageClassName;
    private String requestsSize;
    private String accessModes;

    @DataBoundConstructor
    public GenericEphemeralWorkspaceVolume() {}

    @Override
    public String getStorageClassName() {
        return storageClassName;
    }

    @DataBoundSetter
    public void setStorageClassName(String storageClassName) {
        this.storageClassName = Util.fixEmptyAndTrim(storageClassName);
    }

    @Override
    public String getRequestsSize() {
        return requestsSize;
    }

    @DataBoundSetter
    public void setRequestsSize(@CheckForNull String requestsSize) {
        this.requestsSize = Util.fixEmptyAndTrim(requestsSize);
    }

    @Override
    public String getAccessModes() {
        return accessModes;
    }

    @DataBoundSetter
    public void setAccessModes(String accessModes) {
        this.accessModes = accessModes;
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return buildEphemeralVolume(volumeName);
    }

    @Extension
    @Symbol("genericEphemeralVolume")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Generic Ephemeral Volume";
        }

        @SuppressWarnings("unused") // by stapler
        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems() {
            return PVCVolumeUtils.ACCESS_MODES_BOX;
        }
    }
}
