package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.substituteEnv;

public class PodLabel extends AbstractDescribableImpl<PodLabel> implements Serializable {

    private static final long serialVersionUID = -5667326362260252552L;

    private String key;
    private String value;

    @DataBoundConstructor
    public PodLabel(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Create map from collection of labels. Values that environment variables placeholders will be resolved.
     * @see PodTemplateUtils#substituteEnv(String)
     * @param labels collection of pod labels to convert to a map
     * @return immutable map of pod labels
     */
    @NotNull
    static Map<String, String> toMap(@NotNull Iterable<PodLabel> labels) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
        if (labels != null) {
            for (PodLabel podLabel : labels) {
                builder.put(podLabel.getKey(), substituteEnv(podLabel.getValue()));
            }
        }
        return builder.build();
    }

    /**
     * Create list of pod labels from a map of label key values.
     * @param labels labels map
     * @return list of pod labels
     */
    @NotNull
    static List<PodLabel> fromMap(@NotNull Map<String, String> labels) {
        List<PodLabel> list = new ArrayList<>();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            list.add(new PodLabel(label.getKey(), label.getValue()));
        }
        return list;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PodLabel that = (PodLabel) o;

        return key != null ? key.equals(that.key) : that.key == null;

    }

    @Override
    public int hashCode() {
        return key != null ? key.hashCode() : 0;
    }

    @Extension
    @Symbol("podLabel")
    public static class DescriptorImpl extends Descriptor<PodLabel> {
        @Override
        public String getDisplayName() {
            return "Pod Label";
        }
    }
}
