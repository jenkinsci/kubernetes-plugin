package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;

/**
 * Deprecated, use KeyValueEnvVar
 */
@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "Serialization happens exclusively through XStream and not Java Serialization.")
@Deprecated
public class ContainerEnvVar extends KeyValueEnvVar {

    @DataBoundConstructor
    public ContainerEnvVar(String key, String value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return "ContainerEnvVar [getValue()=" + getValue() + ", getKey()=" + getKey() + "]";
    }

    @Extension
    @Symbol("containerEnvVar")
    /**
     * deprecated, use envVar
     */
    public static class DescriptorImpl extends KeyValueEnvVar.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return "[Deprecated: Use Environment Variable] Container Environment Variable";
        }
    }

    @Extension
    public static class DescriptorVisibilityFilterImpl extends DescriptorVisibilityFilter {
        @Override
        public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
            return !(descriptor instanceof ContainerEnvVar.DescriptorImpl);
        }
    }
}
