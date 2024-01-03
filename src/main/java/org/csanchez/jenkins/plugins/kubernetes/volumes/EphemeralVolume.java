package org.csanchez.jenkins.plugins.kubernetes.volumes;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

/**
 * Interface containing common code between {@link GenericEphemeralVolume} and {@link org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.GenericEphemeralWorkspaceVolume}.
 */
public interface EphemeralVolume extends ProvisionedVolume {
    default Volume buildEphemeralVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewEphemeral()
                .withNewVolumeClaimTemplate()
                .withNewSpec()
                .withAccessModes(getAccessModesOrDefault())
                .withStorageClassName(getStorageClassNameOrDefault())
                .withNewResources()
                .withRequests(getResourceMap())
                .endResources()
                .endSpec()
                .endVolumeClaimTemplate()
                .endEphemeral()
                .build();
    }
}
