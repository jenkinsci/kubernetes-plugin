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
public class PodEnvVar extends KeyValueEnvVar {

    private static final long serialVersionUID = 5426623531408300311L;

    @DataBoundConstructor
    public PodEnvVar(String key, String value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return "PodEnvVar [getValue()=" + getValue() + ", getKey()=" + getKey() + "]";
    }

    public Descriptor<KeyValueEnvVar> getDescriptor() {
        return new DescriptorImpl();
    }

    @Extension
    @Symbol("podEnvVar")
    public static class DescriptorImpl extends Descriptor<KeyValueEnvVar> {
        @Override
        public String getDisplayName() {
            return "Global Environment Variable (applied to all containers)";
        }
    }
}
