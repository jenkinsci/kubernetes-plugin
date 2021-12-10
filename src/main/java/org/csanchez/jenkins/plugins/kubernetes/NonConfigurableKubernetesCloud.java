package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

public class NonConfigurableKubernetesCloud extends KubernetesCloud {
    public NonConfigurableKubernetesCloud(@NonNull String name, @NonNull KubernetesCloud source) {
        super(name, source);
    }

    @Extension
    public static class FilterImpl extends DescriptorVisibilityFilter {
        @Override
        public boolean filter(Object context, Descriptor descriptor) {
            return !(descriptor instanceof DescriptorImpl);
        }
    }

    @Extension
    public static class DescriptorImpl extends KubernetesCloud.DescriptorImpl {
        @Override
        public String getDisplayName() {
            return Messages.NonConfigurableKubernetesCloud_displayName();
        }

        @Override
        public boolean configure(StaplerRequest request, JSONObject object) throws Descriptor.FormException {
            return true;
        }

        @Override
        public Cloud newInstance(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            if (req != null) {
                // We prevent the cloud reconfiguration from the web UI
                String cloudName = req.getParameter("cloudName");
                return Jenkins.get().getCloud(cloudName);
            } else {
                throw new IllegalStateException("Expecting req to be non-null");
            }
        }
    }
}
