package org.csanchez.jenkins.plugins.kubernetes.affinities;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.*;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

public class NodeAffinity extends Affinity {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private boolean isRequiredDuringSchedulingIgnoredDuringExecution;
    private boolean isPreferredDuringSchedulingIgnoredDuringExecution;
    private String nodeSelectorTerms;
    private String preferredTerms;

    @DataBoundConstructor
    public NodeAffinity(boolean isRequiredDuringSchedulingIgnoredDuringExecution,
                        boolean isPreferredDuringSchedulingIgnoredDuringExecution,
                        String nodeSelectorTerms,
                        String preferredTerms
    ) {
        this.isRequiredDuringSchedulingIgnoredDuringExecution =
                isRequiredDuringSchedulingIgnoredDuringExecution;
        this.isPreferredDuringSchedulingIgnoredDuringExecution =
                isPreferredDuringSchedulingIgnoredDuringExecution;
        this.nodeSelectorTerms = nodeSelectorTerms;
        this.preferredTerms = preferredTerms;
    }

    public io.fabric8.kubernetes.api.model.NodeAffinity buildAffinity() throws IOException {
        io.fabric8.kubernetes.api.model.NodeAffinity nodeAffinity = new io.fabric8.kubernetes.api.model.NodeAffinity();
        if(isRequiredDuringSchedulingIgnoredDuringExecution) {
            nodeAffinity.setRequiredDuringSchedulingIgnoredDuringExecution(getNodeSelectors(nodeSelectorTerms));
        }
        if(isPreferredDuringSchedulingIgnoredDuringExecution) {
            nodeAffinity.setPreferredDuringSchedulingIgnoredDuringExecution(getPreferredSchedulingTermList(preferredTerms));
        }
        return nodeAffinity;
    }

    @Extension
    @Symbol("nodeAffinity")
    public static class DescriptorImpl extends Descriptor<Affinity> {
        @Override
        public String getDisplayName() {
            return "Node Affinity";
        }
    }

    private NodeSelector getNodeSelectors(String nodeSelectorTerms) throws IOException {
        List<NodeSelectorTerm> nodeSelectorTermsList = OBJECT_MAPPER.readValue(nodeSelectorTerms,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, NodeSelectorTerm.class));
        NodeSelector nodeSelector = new NodeSelector();
        nodeSelector.setNodeSelectorTerms(nodeSelectorTermsList);
        return nodeSelector;
    }

    private List<PreferredSchedulingTerm> getPreferredSchedulingTermList(String preferredTerms) throws IOException {
        List<PreferredSchedulingTerm> preferredSchedulingTerms = OBJECT_MAPPER.readValue(preferredTerms,
                        OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PreferredSchedulingTerm.class));
        return preferredSchedulingTerms;
    }
}
