package org.csanchez.jenkins.plugins.kubernetes;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import java.io.Serializable;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;

/**
 * Deprecated, use KeyValueEnvVar
 */
@Deprecated
public class ContainerEnvVar extends AbstractDescribableImpl<ContainerEnvVar> implements Serializable, TemplateEnvVar{

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

    @Override
    public EnvVar buildEnvVar() {
        return new EnvVarBuilder().withName(getKey()).withValue(getValue()).build();
    }

    @Extension
    @Symbol("containerEnvVar")
    /**
     * deprecated, use envVar
     */
    public static class DescriptorImpl extends Descriptor<ContainerEnvVar> {
        @Override
        public String getDisplayName() {
            return "Environment Variable";
        }
    }

}
