package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import hudson.Extension;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class Evicted extends PodRetention implements Serializable {

    @Serial
    private static final long serialVersionUID = 6424267627207206819L;

    private static final Logger LOGGER = Logger.getLogger(Evicted.class.getName());

    @DataBoundConstructor
    public Evicted() {}

    @Override
    public boolean shouldDeletePod(KubernetesCloud cloud, Supplier<Pod> podS) {
        Pod pod = null;
        try {
            pod = podS.get();
        } catch (RuntimeException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        boolean isEvicted = pod != null
                && pod.getStatus() != null
                && pod.getStatus().getReason() != null
                && pod.getStatus().getReason().toLowerCase(Locale.getDefault()).equals("evicted");
        return !isEvicted;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof Evicted) {
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
        return Messages.evicted();
    }

    @Extension
    @Symbol("evicted")
    public static class DescriptorImpl extends PodRetentionDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.evicted();
        }
    }
}
