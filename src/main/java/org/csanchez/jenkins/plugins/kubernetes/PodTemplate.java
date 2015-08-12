package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Set;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class PodTemplate extends AbstractDescribableImpl<PodTemplate> {

    private final String name;

    private final String image;

    private boolean privileged;

    private final String command;

    private final String remoteFs;

    private final int instanceCap;

    private final String label;


    @DataBoundConstructor
    public PodTemplate(String name, String image, String command, String remoteFs, int instanceCap, String label) {
        this.name = name;
        this.image = image;
        this.command = command;
        this.remoteFs = remoteFs;
        this.instanceCap = instanceCap;
        this.label = label;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public boolean isPrivileged() {
        return privileged;
    }

    public String getCommand() {
        return command;
    }

    public String getDisplayName() {
        return "Kubernetes Pod Template";
    }

    public String getRemoteFs() {
        return remoteFs;
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    public String getLabel() {
        return label;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodTemplate> {

        @Override
        public String getDisplayName() {
            return "Kubernetes Pod Template";
        }
    }
}
