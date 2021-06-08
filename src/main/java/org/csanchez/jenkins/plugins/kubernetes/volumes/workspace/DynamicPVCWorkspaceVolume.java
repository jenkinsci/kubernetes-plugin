package org.csanchez.jenkins.plugins.kubernetes.volumes.workspace;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud.DEFAULT_POD_LABELS;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.substituteEnv;

/**
 * @author <a href="root@junwuhui.cn">runzexia</a>
 */
public class DynamicPVCWorkspaceVolume extends WorkspaceVolume {
    private String storageClassName;
    private String requestsSize;
    private String accessModes;
    private static final Logger LOGGER = Logger.getLogger(DynamicPVCWorkspaceVolume.class.getName());

    @DataBoundConstructor
    public DynamicPVCWorkspaceVolume() {}

    public DynamicPVCWorkspaceVolume(String storageClassName,
                                     String requestsSize, String accessModes) {
        this.storageClassName = storageClassName;
        this.requestsSize = requestsSize;
        this.accessModes = accessModes;
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
    public Volume buildVolume(String volumeName, String podName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim()
                .withClaimName("pvc-" + podName)
                .withReadOnly(false)
                .and()
                .build();
    }

    @Override
    public PersistentVolumeClaim createVolume(KubernetesClient client, ObjectMeta podMetaData){
        String namespace = podMetaData.getNamespace();
        String podId = podMetaData.getName();
        LOGGER.log(Level.FINE, "Adding workspace volume from pod: {0}/{1}", new Object[] { namespace, podId });
        OwnerReference ownerReference = new OwnerReferenceBuilder().
                withApiVersion("v1").
                withKind("Pod").
                withBlockOwnerDeletion(true).
                withController(true).
                withName(podMetaData.getName()).
                withUid(podMetaData.getUid()).build();

         PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName("pvc-" + podMetaData.getName())
                .withOwnerReferences(ownerReference)
                .withLabels(DEFAULT_POD_LABELS)
                .endMetadata()
                .withNewSpec()
                .withAccessModes(getAccessModesOrDefault())
                .withNewResources()
                .withRequests(getResourceMap())
                .endResources()
                .withStorageClassName(getStorageClassNameOrDefault())
                .endSpec()
                .build();
         pvc = client.persistentVolumeClaims().inNamespace(podMetaData.getNamespace()).create(pvc);
         LOGGER.log(INFO, "Created PVC: {0}/{1}", new Object[] { namespace, pvc.getMetadata().getName() });
         return pvc;
    }

    public String getStorageClassNameOrDefault(){
        if (getStorageClassName() != null) {
            return getStorageClassName();
        }
        return null;
    }

    public String getAccessModesOrDefault() {
        if (getAccessModes() != null) {
            return getAccessModes();
        }
        return "ReadWriteOnce";
    }

    public String getRequestsSizeOrDefault() {
        if (getRequestsSize() != null) {
            return getRequestsSize();
        }
        return "10Gi";
    }

    protected Map<String, Quantity> getResourceMap() {
        String actualStorage = substituteEnv(getRequestsSizeOrDefault());
        Quantity storageQuantity = new Quantity(actualStorage);
        return Collections.singletonMap("storage", storageQuantity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamicPVCWorkspaceVolume that = (DynamicPVCWorkspaceVolume) o;
        return Objects.equals(storageClassName, that.storageClassName) &&
                Objects.equals(requestsSize, that.requestsSize) &&
                Objects.equals(accessModes, that.accessModes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageClassName, requestsSize, accessModes);
    }

    @Extension
    @Symbol("dynamicPVC")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {

        private static final ListBoxModel ACCESS_MODES_BOX = new ListBoxModel()
                .add("ReadWriteOnce")
                .add("ReadOnlyMany")
                .add("ReadWriteMany");

        @Override
        public String getDisplayName() {
            return "Dynamic Persistent Volume Claim Workspace Volume";
        }

        @SuppressWarnings("unused") // by stapler
        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems(){
            return ACCESS_MODES_BOX;
        }
    }
}
