package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import java.io.Serializable;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import io.fabric8.kubernetes.api.model.Pod;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnFailure extends PodRetention implements Serializable {

    private static final long serialVersionUID = 6424267627207206819L;

    private static final Logger LOGGER = Logger.getLogger(OnFailure.class.getName());

    @DataBoundConstructor
    public OnFailure() {

    }

    @Override
    public boolean shouldDeletePod(KubernetesCloud cloud, Supplier<Pod> podS) {
        Pod pod = null;
        try {
            pod = podS.get();
        } catch (RuntimeException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        if (pod == null || pod.getStatus() == null) {
            return false;
        }
        boolean hasErrors = pod.getStatus().getPhase().toLowerCase(Locale.getDefault()).matches("(failed|unknown)");
        return !hasErrors;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof OnFailure) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        return Messages.on_Failure();
    }

    @Extension
    @Symbol("onFailure")
    public static class DescriptorImpl extends PodRetentionDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.on_Failure();
        }

    }
}
