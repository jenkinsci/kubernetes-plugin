package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.util.List;
import java.util.stream.Collectors;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Environment variables that are meant to be applied to all containers.
 */
public class PodEnvVar extends AbstractDescribableImpl<PodEnvVar> {

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

    static List<ContainerEnvVar> asContainerEnvVar(List<PodEnvVar> list) {
        return list.stream().map((var) -> new ContainerEnvVar(var.getKey(), var.getValue()))
                .collect(Collectors.toList());
    }

    static List<PodEnvVar> fromContainerEnvVar(List<ContainerEnvVar> list) {
        return list.stream().map((var) -> new PodEnvVar(var.getKey(), var.getValue())).collect(Collectors.toList());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodEnvVar> {
        @Override
        public String getDisplayName() {
            return "Global Environment Variable (applied to all containers)";
        }
    }
}