package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Environment variables created from kubernetes secrets that are meant to be applied to all containers.
 */
public class PodSecretEnvVar extends AbstractPodEnvVar {

    private String secretName;
    private String secretKey;

    @DataBoundConstructor
    public PodSecretEnvVar(String key, String secretName, String secretKey) {
        this.key = key;
        this.secretName = secretName;
        this.secretKey = secretKey;
    }

    @Override
    public EnvVar buildEnvVar() {
        return new EnvVarBuilder()
                .withName(key)
                .withValueFrom(new EnvVarSourceBuilder()
                        .withSecretKeyRef(new SecretKeySelectorBuilder().withKey(secretKey).withName(secretName).build())
                        .build())
                .build();
    }

    public String getValue() {
        return "remove-me";
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String toString() {
        return String.format("%s=%s=%s", key, secretName, secretKey);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((secretName == null) ? 0 : secretName.hashCode());
        result = prime * result + ((secretKey == null) ? 0 : secretKey.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PodSecretEnvVar other = (PodSecretEnvVar) obj;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (secretName == null) {
            if (other.secretName != null)
                return false;
        } else if (!secretName.equals(other.secretName))
            return false;
        if (secretKey == null) {
            if (other.secretKey != null)
                return false;
        } else if (!secretKey.equals(other.secretKey))
            return false;
        return true;
    }

    @Extension
    @Symbol("podSecretEnvVar")
    public static class DescriptorImpl extends Descriptor<AbstractPodEnvVar> {
        @Override
        public String getDisplayName() {
            return "Global Secret Environment Variable (applied to all containers)";
        }
    }
}