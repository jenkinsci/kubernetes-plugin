package org.csanchez.jenkins.plugins.kubernetes.affinities;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

public class PodAffinity extends Affinity {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private boolean isRequiredDuringSchedulingIgnoredDuringExecution;
    private boolean isPreferredDuringSchedulingIgnoredDuringExecution;
    private String weightedPodAffinityTerms;
    private String podAffinityTerms;


    @DataBoundConstructor
    public PodAffinity(boolean isRequiredDuringSchedulingIgnoredDuringExecution,
                       boolean isPreferredDuringSchedulingIgnoredDuringExecution,
                       String weightedPodAffinityTerms,
                       String podAffinityTerms
    ) {
        this.isRequiredDuringSchedulingIgnoredDuringExecution = isRequiredDuringSchedulingIgnoredDuringExecution;
        this.isPreferredDuringSchedulingIgnoredDuringExecution = isPreferredDuringSchedulingIgnoredDuringExecution;
        this.weightedPodAffinityTerms = weightedPodAffinityTerms;
        this.podAffinityTerms = podAffinityTerms;
    }

    public io.fabric8.kubernetes.api.model.PodAffinity buildAffinity() throws IOException {
        io.fabric8.kubernetes.api.model.PodAffinity podAffinity = new io.fabric8.kubernetes.api.model.PodAffinity();
        if(isRequiredDuringSchedulingIgnoredDuringExecution) {
            podAffinity.setRequiredDuringSchedulingIgnoredDuringExecution(getPodAffinityTerms(podAffinityTerms));
        }
        if(isPreferredDuringSchedulingIgnoredDuringExecution) {
            podAffinity.setPreferredDuringSchedulingIgnoredDuringExecution(getWeightedPodAffinityTerms(weightedPodAffinityTerms));
        }
        return podAffinity;
    }

    private List<WeightedPodAffinityTerm> getWeightedPodAffinityTerms(String weightedPodAffinityTerms) throws IOException {
        List<WeightedPodAffinityTerm> weightedPodAffinityTermList = OBJECT_MAPPER.readValue(weightedPodAffinityTerms,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, WeightedPodAffinityTerm.class));
        return weightedPodAffinityTermList;
    }

    private List<PodAffinityTerm> getPodAffinityTerms(String podAffinityTerms) throws IOException {
        List<PodAffinityTerm> podAffinityTermList = OBJECT_MAPPER.readValue(podAffinityTerms,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PodAffinityTerm.class));
        return podAffinityTermList;
    }

    @Extension
    @Symbol("podAffinity")
    public static class DescriptorImpl extends Descriptor<Affinity> {
        @Override
        public String getDisplayName() {
            return "Pod Affinity";
        }
    }
}
