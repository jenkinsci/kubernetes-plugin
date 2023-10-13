package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.slaves.Cloud;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;

@Extension
public class PodTemplateAction  implements Action{
    
    @Override
    public String getIconFileName() {
        return "symbol-copy-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        return "Pod templates";
    }

    @Override
    public String getUrlName() {
        return "templates";
    }

    @Extension
    public static final class CloudActionFactory extends TransientActionFactory<Cloud> {
        @Override
        public Class<Cloud> type() {
            return Cloud.class;
        }

        @NonNull
        @Override
        public Collection<? extends Action> createFor(@NonNull Cloud target) {
            if (target instanceof KubernetesCloud) {
                return Collections.singleton(new PodTemplateAction());
            }
            return Collections.emptyList();
        }
    }
}
