package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import java.io.Serializable;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import io.fabric8.kubernetes.api.model.Pod;

public class Never extends PodRetention implements Serializable {

    private static final long serialVersionUID = -7127652621214283411L;

    @DataBoundConstructor
    public Never() {

    }

    @Override
    public boolean shouldDeletePod(KubernetesCloud cloud, Pod pod) {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof Never) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Extension
    @Symbol("never")
    public static class DescriptorImpl extends PodRetentionDescriptor {
        
        @Override
        public String getDisplayName() {
            return Messages.never();
        }
    }

}