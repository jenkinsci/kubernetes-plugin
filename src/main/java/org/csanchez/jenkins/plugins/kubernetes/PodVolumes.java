package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

public class PodVolumes {
    /**
     * Base class for all Kubernetes volume types
     */
    public static abstract class PodVolume extends AbstractDescribableImpl<PodVolume> {
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
}