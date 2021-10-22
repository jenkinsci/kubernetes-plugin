package org.csanchez.jenkins.plugins.kubernetes.pod.yaml;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import io.fabric8.kubernetes.api.model.Pod;

import java.io.Serializable;
import java.util.List;

public abstract class YamlMergeStrategy extends AbstractDescribableImpl<YamlMergeStrategy> implements ExtensionPoint, Serializable {
    public static YamlMergeStrategy defaultStrategy() {
        return new Overrides();
    }

    public abstract Pod merge(@NonNull List<String> yamls);

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
