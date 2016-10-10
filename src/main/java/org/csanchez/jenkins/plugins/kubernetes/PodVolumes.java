package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.List;

public class PodVolumes {
    /**
     * Base class for all Kubernetes volume types
     */
    public static abstract class PodVolume extends AbstractDescribableImpl<PodVolume> implements Serializable {
        // Where to mount this volume in the pod.
        public abstract String getMountPath();

        // Builds a Volume model with the given name.
        public abstract Volume buildVolume(String volumeName);
    }

    public static class EmptyDirVolume extends PodVolume {

        private static final String DEFAULT_MEDIUM = "";
        private static final String MEMORY_MEDIUM = "Memory";

        private String mountPath;
        private Boolean memory;

        @DataBoundConstructor
        public EmptyDirVolume(String mountPath, Boolean memory) {
            this.mountPath = mountPath;
            this.memory = memory;
        }

        @Override
        public String getMountPath() {
            return mountPath;
        }

        public String getMedium() {
           return memory ? MEMORY_MEDIUM : DEFAULT_MEDIUM;
        }

        @Override
        public Volume buildVolume(String volumeName) {
            return new VolumeBuilder().withName(volumeName)
                    .withNewEmptyDir(getMedium())
                    .build();
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PodVolume> {
            @Override
            public String getDisplayName() {
                return "Empty Dir Volume";
            }
        }
    }

    public static class SecretVolume extends PodVolume {

        private String mountPath;
        private String secretName;

        @DataBoundConstructor
        public SecretVolume(String mountPath, String secretName) {
            this.mountPath = mountPath;
            this.secretName = secretName;
        }

        @Override
        public Volume buildVolume(String volumeName) {
            return new VolumeBuilder()
                    .withName(volumeName)
                    .withNewSecret(getSecretName())
                    .build();
        }

        public String getSecretName() {
            return secretName;
        }

        @Override
        public String getMountPath() {
            return mountPath;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PodVolume> {
            @Override
            public String getDisplayName() {
                return "Secret Volume";
            }
        }
    }

    public static class HostPathVolume extends PodVolume {
        private String mountPath;
        private String hostPath;

        @DataBoundConstructor
        public HostPathVolume(String hostPath, String mountPath) {
            this.hostPath = hostPath;
            this.mountPath = mountPath;
        }

        public Volume buildVolume(String volumeName) {
            return new VolumeBuilder()
                    .withName(volumeName)
                    .withNewHostPath(getHostPath())
                    .build();
        }

        public String getMountPath() {
            return mountPath;
        }

        public String getHostPath() {
            return hostPath;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PodVolume> {
            @Override
            public String getDisplayName() {
                return "Host Path Volume";
            }
        }
    }

    public static class NfsVolume extends PodVolume {
        private String mountPath;
        private String serverAddress;
        private String serverPath;
        private Boolean readOnly;

        @DataBoundConstructor
        public NfsVolume(String serverAddress, String serverPath, Boolean readOnly, String mountPath) {
            this.serverAddress = serverAddress;
            this.serverPath = serverPath;
            this.readOnly = readOnly;
            this.mountPath = mountPath;
        }

        public Volume buildVolume(String volumeName) {
            return new VolumeBuilder()
                    .withName(volumeName)
                    .withNewNfs(getServerPath(), getReadOnly(), getServerAddress())
                    .build();
        }

        public String getMountPath() {
            return mountPath;
        }

        public String getServerAddress() {
            return serverAddress;
        }

        public String getServerPath() {
            return serverPath;
        }

        public Boolean getReadOnly() {
            return readOnly;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PodVolume> {
            @Override
            public String getDisplayName() {
                return "NFS Volume";
            }
        }
    }

    public static class PvcVolume extends PodVolume {
        private String mountPath;
        private String claimName;
        private Boolean readOnly;

        @DataBoundConstructor
        public PvcVolume(String mountPath, String claimName, Boolean readOnly) {
            this.mountPath = mountPath;
            this.claimName = claimName;
            this.readOnly = readOnly;
        }

        @Override
        public String getMountPath() {
            return mountPath;
        }

        public String getClaimName() {
            return claimName;
        }

        public Boolean getReadOnly() {
            return readOnly;
        }

        @Override
        public Volume buildVolume(String volumeName) {
            return new VolumeBuilder()
                    .withName(volumeName)
                    .withNewPersistentVolumeClaim()
                        .withClaimName(claimName)
                        .withReadOnly(readOnly)
                    .and()
                    .build();
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PodVolume> {
            @Override
            public String getDisplayName() {
                return "Persistent Volume Claim";
            }
        }
    }

    public static boolean volumeMountExists(String path, List<VolumeMount> existingMounts) {
        for (VolumeMount mount : existingMounts) {
            if (mount.getMountPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public static boolean podVolumeExists(String path, List<PodVolume> existingVolumes) {
        for (PodVolume podVolume : existingVolumes) {
            if (podVolume.getMountPath().equals(path)) {
                return true;
            }
        }
        return false;
    }
}