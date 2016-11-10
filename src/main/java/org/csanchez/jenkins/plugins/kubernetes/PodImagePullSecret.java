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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PodImagePullSecret that = (PodImagePullSecret) o;

        return name != null ? name.equals(that.name) : that.name == null;

    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodImagePullSecret> {
        @Override
        public String getDisplayName() {
            return "Image Pull Secret";
        }
    }
}