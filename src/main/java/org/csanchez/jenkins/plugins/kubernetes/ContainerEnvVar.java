package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class ContainerEnvVar extends AbstractDescribableImpl<ContainerEnvVar> {

    private String key;
    private String value;

    @DataBoundConstructor
    public ContainerEnvVar(String key, String value) {
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

    @Extension
    public static class DescriptorImpl extends Descriptor<ContainerEnvVar> {
        @Override
        public String getDisplayName() {
            return "Container Environment Variable";
        }
    }
}