package org.csanchez.jenkins.plugins.kubernetes.volumes;

import static java.util.logging.Level.INFO;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Interface containing common code between {@link DynamicPVCVolume} and {@link org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume}.
 */
public interface DynamicPVC extends ProvisionedVolume {
    Logger LOGGER = Logger.getLogger(DynamicPVC.class.getName());

    default Volume buildPVC(String volumeName, String podName) {
        String pvcName = getPvcName(podName);
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim()
                .withClaimName(pvcName)
                .withReadOnly(false)
                .endPersistentVolumeClaim()
                .build();
    }

    default PersistentVolumeClaim createPVC(KubernetesClient client, ObjectMeta podMetaData) {
        String namespace = podMetaData.getNamespace();
        String podName = podMetaData.getName();
        LOGGER.log(Level.FINE, "Adding volume for pod: {0}/{1}", new Object[] {namespace, podName});
        OwnerReference ownerReference = new OwnerReferenceBuilder()
                .withApiVersion("v1")
                .withKind("Pod")
                .withController(true)
                .withName(podMetaData.getName())
                .withUid(podMetaData.getUid())
                .build();

        String pvcName = getPvcName(podMetaData.getName());
        PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(pvcName)
                .withLabels(podMetaData.getLabels())
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
        pvc = client.persistentVolumeClaims()
                .inNamespace(podMetaData.getNamespace())
                .resource(pvc)
                .create();
        LOGGER.log(INFO, "Created PVC: {0}/{1}", new Object[] {namespace, pvcName});
        return pvc;
    }

    String getPvcName(String podName);
}
