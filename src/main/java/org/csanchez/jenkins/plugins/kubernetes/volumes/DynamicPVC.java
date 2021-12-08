package org.csanchez.jenkins.plugins.kubernetes.volumes;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;

import static java.util.logging.Level.INFO;

/**
 * Interface containing common code between {@link DynamicPVCVolume} and {@link org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume}.
 */
public interface DynamicPVC {
    Logger LOGGER = Logger.getLogger(DynamicPVC.class.getName());

    default Volume buildPVC(String volumeName, String podName){
        String pvcName = getPvcName(podName);
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim()
                .withClaimName(pvcName)
                .withReadOnly(false)
                .endPersistentVolumeClaim()
                .build();
    }

    default PersistentVolumeClaim createPVC(KubernetesClient client, ObjectMeta podMetaData){
        String namespace = podMetaData.getNamespace();
        String podName = podMetaData.getName();
        LOGGER.log(Level.FINE, "Adding volume from pod: {0}/{1}", new Object[] { namespace, podName });
        OwnerReference ownerReference = new OwnerReferenceBuilder().
                withApiVersion("v1").
                withKind("Pod").
                withBlockOwnerDeletion(true).
                withController(true).
                withName(podMetaData.getName()).
                withUid(podMetaData.getUid()).build();

        String pvcName = getPvcName(podMetaData.getName());
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(pvcName)
                .withOwnerReferences(ownerReference)
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
        LOGGER.log(INFO, "Created PVC: {0}/{1}", new Object[] { namespace, pvcName });
        return pvc;
    }

    default String getStorageClassNameOrDefault(){
        return getStorageClassName();
    }

    String getStorageClassName();

    default Map<String, Quantity> getResourceMap() {
        return Collections.singletonMap("storage", new Quantity(getRequestsSizeOrDefault()));
    }

    default String getRequestsSizeOrDefault() {
        return StringUtils.defaultString(getRequestsSize(), "10Gi");
    }

    String getRequestsSize();

    default String getAccessModesOrDefault() {
        return StringUtils.defaultString(getAccessModes(), "ReadWriteOnce");
    }

    String getAccessModes();

    String getPvcName(String podName);
}
