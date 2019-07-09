package org.csanchez.jenkins.plugins.kubernetes.pod.yaml;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Pod;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.parseFromYaml;

public class Overrides extends YamlMergeStrategy implements Serializable {
    private static final long serialVersionUID = 4510341864619338164L;

    @DataBoundConstructor
    public Overrides() {
    }

    @Override
    public Pod merge(List<String> yamls) {
        if (yamls.isEmpty()) {
            return null;
        } else {
            return parseFromYaml(yamls.get(yamls.size()-1));
        }
    }

    @Extension
    @Symbol("override")
    public static class DescriptorImpl extends Descriptor<YamlMergeStrategy> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Override";
        }
    }
}
