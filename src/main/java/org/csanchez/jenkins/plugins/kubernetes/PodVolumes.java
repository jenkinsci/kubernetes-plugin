package org.csanchez.jenkins.plugins.kubernetes;

import io.fabric8.kubernetes.api.model.VolumeMount;

import java.util.List;

@Deprecated
public class PodVolumes {

    @Deprecated
    public static abstract class PodVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume {
    }

    @Deprecated
    public static class EmptyDirVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume {

        public EmptyDirVolume(String mountPath, Boolean memory) {
            super(mountPath, memory, null);
        }

        protected Object readResolve() {
            return new org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume(this.getMountPath(),
                    this.getMemory(), null);
        }
    }

    @Deprecated
    public static class SecretVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.SecretVolume {

        public SecretVolume(String mountPath, String secretName) {
            super(mountPath, secretName);
        }

        protected Object readResolve() {
            return new org.csanchez.jenkins.plugins.kubernetes.volumes.SecretVolume(this.getMountPath(),
                    this.getSecretName());
        }
    }

    @Deprecated
    public static class HostPathVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume {

        public HostPathVolume(String hostPath, String mountPath) {
            super(hostPath, mountPath, null);
        }

        protected Object readResolve() {
            return new org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume(this.getHostPath(),
                    this.getMountPath(), null);
        }
    }

    @Deprecated
    public static class NfsVolume extends org.csanchez.jenkins.plugins.kubernetes.volumes.NfsVolume {

        public NfsVolume(String serverAddress, String serverPath, Boolean readOnly, String mountPath) {
            super(serverAddress, serverPath, readOnly, mountPath, null);
        }

        protected Object readResolve() {
            return new org.csanchez.jenkins.plugins.kubernetes.volumes.NfsVolume(this.getServerAddress(),
                    this.getServerPath(), this.getReadOnly(), this.getMountPath(), null);
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
