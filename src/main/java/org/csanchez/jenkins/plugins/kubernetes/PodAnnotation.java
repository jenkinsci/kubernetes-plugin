package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class PodAnnotation extends AbstractDescribableImpl<PodAnnotation> implements Serializable {

    private static final long serialVersionUID = -5667326362260252552L;

    private String key;
    private String value;

    @DataBoundConstructor
    public PodAnnotation(String key, String value) {
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

    @Extension
    @Symbol("podAnnotation")
    public static class DescriptorImpl extends Descriptor<PodAnnotation> {
        @Override
        public String getDisplayName() {
            return "Pod Annotation";
        }
    }
}
