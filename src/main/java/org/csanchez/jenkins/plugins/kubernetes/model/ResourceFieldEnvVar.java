package org.csanchez.jenkins.plugins.kubernetes.model;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.ResourceFieldSelectorBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class ResourceFieldEnvVar extends TemplateEnvVar {

    private final static String DEFAULT_DIVISOR = "1";

    private String resource;

    private String containerName;

    private String divisor;

    @DataBoundConstructor
    public ResourceFieldEnvVar(String key, String resource, String containerName, String divisor) {
        super(key);
        this.resource = resource;
        this.containerName = containerName;
        this.divisor = divisor;
    }

    @DataBoundConstructor
    public ResourceFieldEnvVar(String key, String resource, String divisor) {
        this(key, resource, null, divisor);
    }

    @DataBoundConstructor
    public ResourceFieldEnvVar(String key, String resource) {
        this(key, resource, null, DEFAULT_DIVISOR);
    }

    @Override
    public EnvVar buildEnvVar() {
        ResourceFieldSelectorBuilder resourceFieldSelectorBuilder = new ResourceFieldSelectorBuilder()
                .withResource(resource)
                .withNewDivisor(divisor);

        if (containerName != null) {
            resourceFieldSelectorBuilder.withContainerName(containerName);
        }

        return new EnvVarBuilder() //
                .withName(getKey()) //
                .withValueFrom(new EnvVarSourceBuilder() //
                        .withResourceFieldRef(
                                resourceFieldSelectorBuilder.build() //
                        ) //
                        .build()) //
                .build();
    }


    public String getResource() { return resource; }

    public void setResource(String resource) { this.resource = resource; }

    public String getContainerName() { return containerName; }

    public void setContainerName(String containerName) { this.containerName = containerName; }

    public String getDivisor() { return divisor; }

    public void setDivisor(String divisor) { this.divisor = divisor; }

    @Override
    public String toString() {
        return "ResourceFieldEnvVar [resource=" + resource + ",divisor=" + divisor + ",containerName=" + containerName + ", getKey()=" + getKey() + "]";
    }

    @Extension
    @Symbol("resourceFieldEnvVar")
    public static class DescriptorImpl extends Descriptor<TemplateEnvVar> {
        @Override
        public String getDisplayName() {
            return "Environment Variable from Resource Field";
        }
    }

}
