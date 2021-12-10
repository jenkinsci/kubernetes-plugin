package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;

/**
 * Deprecated, use KeyValueEnvVar
 */
@Deprecated
public class PodEnvVar extends KeyValueEnvVar {

    private static final long serialVersionUID = 5426623531408300311L;

    @DataBoundConstructor
    public PodEnvVar(String key, String value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return "PodEnvVar [getValue()=" + getValue() + ", getKey()=" + getKey() + "]";
    }

    @Extension
    @Symbol("podEnvVar")
    public static class DescriptorImpl extends KeyValueEnvVar.DescriptorImpl {
        @Override
        @NonNull
        public String getDisplayName() {
            return "[Deprecated: use Environment Variable] Global Environment Variable (applied to all containers)";
        }
    }

    @Extension
    public static class DescriptorVisibilityFilterImpl extends DescriptorVisibilityFilter {
        @Override
        public boolean filter(@CheckForNull Object context, @NonNull Descriptor descriptor) {
            return !(descriptor instanceof PodEnvVar.DescriptorImpl);
        }
    }
}
