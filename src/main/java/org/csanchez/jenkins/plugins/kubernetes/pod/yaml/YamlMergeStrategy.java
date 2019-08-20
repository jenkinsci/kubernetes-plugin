package org.csanchez.jenkins.plugins.kubernetes.pod.yaml;

import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import io.fabric8.kubernetes.api.model.Pod;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;

public abstract class YamlMergeStrategy extends AbstractDescribableImpl<YamlMergeStrategy> implements ExtensionPoint, Serializable {
    public static YamlMergeStrategy defaultStrategy() {
        return new Overrides();
    }

    public abstract Pod merge(@Nonnull List<String> yamls);
}
