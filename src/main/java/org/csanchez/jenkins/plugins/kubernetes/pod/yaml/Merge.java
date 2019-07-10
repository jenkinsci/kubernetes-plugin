package org.csanchez.jenkins.plugins.kubernetes.pod.yaml;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Pod;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.combine;

public class Merge extends YamlMergeStrategy {
    private static final long serialVersionUID = 6562610892063268131L;

    @DataBoundConstructor
    public Merge() {
    }

    @Override
    public Pod merge(List<String> yamls) {
        return combine(yamls.stream().map(PodTemplateUtils::parseFromYaml).collect(Collectors.toList()));
    }

    @Extension
    @Symbol("merge")
    public static class DescriptorImpl extends Descriptor<YamlMergeStrategy> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "Merge";
        }
    }
}
