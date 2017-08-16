package org.csanchez.jenkins.plugins.kubernetes;

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Deprecated, use KeyValueEnvVar
 */
@Deprecated
public class PodEnvVar extends KeyValueEnvVar {

    @DataBoundConstructor
    public PodEnvVar(String key, String value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return "PodEnvVar [getValue()=" + getValue() + ", getKey()=" + getKey() + "]";
    }

}