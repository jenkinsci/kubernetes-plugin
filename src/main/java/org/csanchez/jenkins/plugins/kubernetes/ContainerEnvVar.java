package org.csanchez.jenkins.plugins.kubernetes;

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;

/**
 * Deprecated, use KeyValueEnvVar
 */
@Deprecated
public class ContainerEnvVar extends KeyValueEnvVar {

    @DataBoundConstructor
    public ContainerEnvVar(String key, String value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return "ContainerEnvVar [getValue()=" + getValue() + ", getKey()=" + getKey() + "]";
    }

    @Extension
    @Symbol("containerEnvVar")
    /**
     * deprecated, use envVar
     */
    public static class DescriptorImplContainer extends Descriptor<KeyValueEnvVar> {
        @Override
        public String getDisplayName() {
            return "Environment Variable";
        }
    }

}
