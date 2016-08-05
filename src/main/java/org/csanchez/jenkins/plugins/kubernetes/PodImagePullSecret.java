package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class PodImagePullSecret extends AbstractDescribableImpl<PodImagePullSecret> {

    private String name;

    @DataBoundConstructor
    public PodImagePullSecret(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodImagePullSecret> {
        @Override
        public String getDisplayName() {
            return "Image Pull Secret";
        }
    }
}