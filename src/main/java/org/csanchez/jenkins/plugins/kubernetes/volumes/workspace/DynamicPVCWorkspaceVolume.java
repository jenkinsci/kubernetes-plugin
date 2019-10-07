package org.csanchez.jenkins.plugins.kubernetes.volumes.workspace;

import com.google.common.collect.ImmutableMap;
import hudson.Extension;
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.util.Map;
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
    public DynamicPVCWorkspaceVolume(String storageClassName,
                                     String requestsSize, String accessModes) {
        this.storageClassName = storageClassName;
        this.requestsSize = requestsSize;
        this.accessModes = accessModes;
    }

    public String getAccessModes() {
        return accessModes;
    }

    public String getRequestsSize() {
        return requestsSize;
    }

    public String getStorageClassName() {
        return storageClassName;
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
        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity>builder();
        String actualStorage = substituteEnv(getRequestsSizeOrDefault());
            Quantity storageQuantity = new Quantity(actualStorage);
            builder.put("storage", storageQuantity);
        return builder.build();
    }

    @Extension
    @Symbol("dynamicPVC")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {

        private static final ListBoxModel ACCESS_MODES_BOX;
        static {
            ListBoxModel boxModel = new ListBoxModel();
            boxModel.add("ReadWriteOnce","ReadWriteOnce");
            boxModel.add("ReadOnlyMany","ReadOnlyMany");
            boxModel.add("ReadWriteMany","ReadWriteMany");
            ACCESS_MODES_BOX = boxModel;
        }
        @Override
        public String getDisplayName() {
            return "Dynamic Persistent Volume Claim Workspace Volume";
        }

        @RequirePOST
        public ListBoxModel doFillAccessModesItems(){
            return ACCESS_MODES_BOX;
        }
    }
}
