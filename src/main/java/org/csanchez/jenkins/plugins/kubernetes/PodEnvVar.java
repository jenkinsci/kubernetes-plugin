package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Environment variables that are meant to be applied to all containers.
 */
public class PodEnvVar extends AbstractDescribableImpl<PodEnvVar> implements Serializable {

    private String key;
    private String value;

    @DataBoundConstructor
    public PodEnvVar(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String toString() {
        return String.format("%s=%s", key, value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        PodEnvVar other = (PodEnvVar) obj;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }


    static List<ContainerEnvVar> asContainerEnvVar(List<PodEnvVar> list) {
        return list.stream().map((var) -> new ContainerEnvVar(var.getKey(), var.getValue()))
                .collect(Collectors.toList());
    }

    static List<PodEnvVar> fromContainerEnvVar(List<ContainerEnvVar> list) {
        return list.stream().map((var) -> new PodEnvVar(var.getKey(), var.getValue())).collect(Collectors.toList());
    }

    @Extension
    @Symbol("podEnvVar")
    public static class DescriptorImpl extends Descriptor<PodEnvVar> {
        @Override
        public String getDisplayName() {
            return "Global Environment Variable (applied to all containers)";
        }
    }

    public static class EnvironmentVariableNames {

        static final String JENKINS_URL = "JENKINS_URL";
        static final String JENKINS_LOCATION_URL = "JENKINS_LOCATION_URL";
        static final String JENKINS_TUNNEL = "JENKINS_TUNNEL";
        static final String JENKINS_SECRET = "JENKINS_SECRET";
        static final String JENKINS_NAME = "JENKINS_NAME";
        static final String JENKINS_JNLP_URL = "JENKINS_JNLP_URL";
        static final String HOME = "HOME";

        private EnvironmentVariableNames() {}

    }
}