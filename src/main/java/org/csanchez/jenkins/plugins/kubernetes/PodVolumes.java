package org.csanchez.jenkins.plugins.kubernetes;

import java.util.List;

import io.fabric8.kubernetes.api.model.VolumeMount;

@Deprecated
public class PodVolumes {

    @Deprecated
    public static abstract class PodVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume {
    }

    @Deprecated
    public static class EmptyDirVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume {

        private static final String DEFAULT_MEDIUM = "";
        private static final String MEMORY_MEDIUM = "Memory";

        public EmptyDirVolume(String mountPath, Boolean memory) {
            super(mountPath, memory);
        }
    }

    @Deprecated
    public static class SecretVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.SecretVolume {

        public SecretVolume(String mountPath, String secretName) {
            super(mountPath, secretName);
        }
    }

    @Deprecated
    public static class ConfigMapVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.ConfigMapVolume {

        public ConfigMapVolume(String mountPath, String configMapName) {
            super(mountPath, configMapName);
        }
    }

    @Deprecated
    public static class HostPathVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume {

        public HostPathVolume(String hostPath, String mountPath) {
            super(hostPath, mountPath);
        }
    }

    @Deprecated
    public static class NfsVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.NfsVolume {

        public NfsVolume(String serverAddress, String serverPath, Boolean readOnly, String mountPath) {
            super(serverAddress, serverPath, readOnly, mountPath);
        }
    }

    @Deprecated
    public static class PvcVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.PvcVolume {

        public PvcVolume(String mountPath, String claimName, Boolean readOnly) {
            super(mountPath, claimName, readOnly);
        }
    }

    /**
     * @deprecated Use {@link PodVolume#volumeMountExists(String, List)} instead
     */
    public static boolean volumeMountExists(String path, List<VolumeMount> existingMounts) {
        return PodVolume.volumeMountExists(path, existingMounts);
    }

    /**
     * @deprecated Use {@link PodVolume#podVolumeExists(String,List)} instead
     */
    public static boolean podVolumeExists(String path, List<PodVolume> existingVolumes) {
        for (PodVolume podVolume : existingVolumes) {
            if (podVolume.getMountPath().equals(path)) {
                return true;
            }
        }
        return false;
    }
}
