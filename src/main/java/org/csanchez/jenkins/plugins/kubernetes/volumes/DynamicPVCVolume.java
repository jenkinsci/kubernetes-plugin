package org.csanchez.jenkins.plugins.kubernetes.volumes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Implements a dynamic PVC volume, that is created before the agent pod is created, and terminated afterwards.
 */
public class DynamicPVCVolume extends PodVolume implements DynamicPVC {
    private String id;
    private String storageClassName;
    private String requestsSize;
    private String accessModes;
    private String mountPath;

    @DataBoundConstructor
    public DynamicPVCVolume() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
    }

    @CheckForNull
    public String getAccessModes() {
        return accessModes;
    }

    @DataBoundSetter
    public void setAccessModes(@CheckForNull String accessModes) {
        this.accessModes = Util.fixEmpty(accessModes);
    }

    @CheckForNull
    public String getRequestsSize() {
        return requestsSize;
    }

    @DataBoundSetter
    public void setRequestsSize(@CheckForNull String requestsSize) {
        this.requestsSize = Util.fixEmpty(requestsSize);
    }

    @CheckForNull
    public String getStorageClassName() {
        return storageClassName;
    }

    @DataBoundSetter
    public void setStorageClassName(@CheckForNull String storageClassName) {
        this.storageClassName = Util.fixEmpty(storageClassName);
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return buildPVC(volumeName, podName);
    }

    @Override
    public PersistentVolumeClaim createVolume(KubernetesClient client, ObjectMeta podMetaData) {
        return createPVC(client, podMetaData);
    }

    @DataBoundSetter
    public void setMountPath(String mountPath) {
        this.mountPath = mountPath;
    }

    @Nonnull
    public String getPvcName(String podName) {
        return "pvc-" + podName + "-" + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamicPVCVolume that = (DynamicPVCVolume) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(storageClassName, that.storageClassName) &&
                Objects.equals(requestsSize, that.requestsSize) &&
                Objects.equals(accessModes, that.accessModes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, storageClassName, requestsSize, accessModes);
    }

    @Extension
    @Symbol("dynamicPVC")
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        public String getDisplayName() {
            return "Dynamic Persistent Volume Claim";
        }

        @SuppressWarnings("unused") // by stapler
        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems(){
            return PVCVolumeUtils.ACCESS_MODES_BOX;
        }
    }
}
