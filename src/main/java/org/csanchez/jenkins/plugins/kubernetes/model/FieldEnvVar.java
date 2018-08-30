package org.csanchez.jenkins.plugins.kubernetes.model;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class FieldEnvVar extends TemplateEnvVar {

    private final static String DEFAULT_API_VERSION = "v1";

    private String fieldPath;

    private String apiVersion;

    @DataBoundConstructor
    public FieldEnvVar(String key, String fieldPath) {
        this(key, fieldPath, DEFAULT_API_VERSION);
    }

    @DataBoundConstructor
    public FieldEnvVar(String key, String fieldPath, String apiVersion) {
        super(key);
        this.fieldPath = fieldPath;
        this.apiVersion = apiVersion;
    }

    @Override
    public EnvVar buildEnvVar() {
        return new EnvVarBuilder() //
                .withName(getKey()) //
                .withValueFrom(new EnvVarSourceBuilder() //
                        .withFieldRef(
                                new ObjectFieldSelectorBuilder() //
                                        .withFieldPath(fieldPath) //
                                        .withApiVersion(apiVersion) //
                                        .build() //
                        ) //
                        .build()) //
                .build();
    }

    public String getFieldPath() { return fieldPath; }

    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }

    public String getApiVersion() { return apiVersion; }

    public String setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    @Override
    public String toString() {
        return "FieldEnvVar [fieldPath=" + fieldPath + ",apiVersion=" + apiVersion + ",getKey()=" + getKey() + "]";
    }

    @Extension
    @Symbol("fieldEnvVar")
    public static class DescriptorImpl extends Descriptor<TemplateEnvVar> {
        @Override
        public String getDisplayName() {
            return "Environment Variable from Object Field";
        }
    }
}
