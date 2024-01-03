package org.csanchez.jenkins.plugins.kubernetes.volumes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.Volume;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Uses a generic ephemeral volume, that is created before the agent pod is created, and terminated afterwards.
 * See <a href="https://kubernetes.io/docs/concepts/storage/ephemeral-volumes/#generic-ephemeral-volumes">Kubernetes documentation</a>
 */
@SuppressFBWarnings(
        value = "SE_NO_SERIALVERSIONID",
        justification = "Serialization happens exclusively through XStream and not Java Serialization.")
public class GenericEphemeralVolume extends PodVolume implements EphemeralVolume {
    private String storageClassName;
    private String requestsSize;
    private String accessModes;
    private String mountPath;

    @DataBoundConstructor
    public GenericEphemeralVolume() {}

    @CheckForNull
    public String getAccessModes() {
        return accessModes;
    }

    @DataBoundSetter
    public void setAccessModes(@CheckForNull String accessModes) {
        this.accessModes = accessModes;
    }

    @CheckForNull
    public String getRequestsSize() {
        return requestsSize;
    }

    @DataBoundSetter
    public void setRequestsSize(@CheckForNull String requestsSize) {
        this.requestsSize = Util.fixEmptyAndTrim(requestsSize);
    }

    @CheckForNull
    public String getStorageClassName() {
        return storageClassName;
    }

    @DataBoundSetter
    public void setStorageClassName(@CheckForNull String storageClassName) {
        this.storageClassName = Util.fixEmptyAndTrim(storageClassName);
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return buildEphemeralVolume(volumeName);
    }

    @DataBoundSetter
    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenericEphemeralVolume that = (GenericEphemeralVolume) o;
        return Objects.equals(storageClassName, that.storageClassName)
                && Objects.equals(requestsSize, that.requestsSize)
                && Objects.equals(accessModes, that.accessModes)
                && Objects.equals(mountPath, that.mountPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageClassName, requestsSize, accessModes, mountPath);
    }

    @Extension
    @Symbol("genericEphemeralVolume")
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Generic ephemeral volume";
        }

        @SuppressWarnings("unused") // by stapler
        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems() {
            return PVCVolumeUtils.ACCESS_MODES_BOX;
        }
    }
}
