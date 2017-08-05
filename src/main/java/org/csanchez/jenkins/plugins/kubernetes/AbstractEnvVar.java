package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.AbstractDescribableImpl;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.io.Serializable;

public abstract class AbstractEnvVar<T extends AbstractDescribableImpl<T>> extends AbstractDescribableImpl<T> implements Serializable {
    protected String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public abstract EnvVar buildEnvVar();
}
