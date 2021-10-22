package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.apache.commons.lang.Validate;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    @NonNull
    static Map<String, String> toMap(@NonNull Iterable<PodLabel> labels) {
        Map<String, String> builder = new HashMap<>();
        for (PodLabel podLabel : labels) {
            builder.put(podLabel.getKey(), substituteEnv(podLabel.getValue()));
        }
        return Collections.unmodifiableMap(builder);
    }

    /**
     * Create list of pod labels from a map of label key values.
     * @param labels labels map
     * @return list of pod labels
     */
    @NonNull
    static List<PodLabel> fromMap(@NonNull Map<String, String> labels) {
        List<PodLabel> list = new ArrayList<>();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            list.add(new PodLabel(label.getKey(), label.getValue()));
        }
        return list;
    }

    static List<PodLabel> listOf(String... keyValue) {
        Validate.isTrue(keyValue.length % 2 == 0, "Expecting an even number of arguments");
        List<PodLabel> labels = new ArrayList<>();
        for (int i = 0; i < keyValue.length / 2; i++) {
            labels.add(new PodLabel(keyValue[2*i], keyValue[2*i+1]));
        }
        return labels;
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

    @Override
    public String toString() {
        return "PodLabel(" + key + " = \"" + value + "\")";
    }

    @Extension
    @Symbol("podLabel")
    public static class DescriptorImpl extends Descriptor<PodLabel> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Pod Label";
        }
    }
}
