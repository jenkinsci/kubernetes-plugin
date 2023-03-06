package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Action;
import hudson.slaves.Cloud;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.TransientActionFactory;
import org.jetbrains.annotations.NotNull;

public class PodTemplatesCloudAction implements Action {
    @Override
    public String getIconFileName() {
        return "symbol-details";
    }

    @Override
    public String getDisplayName() {
        return "Pod Templates";
    }

    @Override
    public String getUrlName() {
        return "podTemplates";
    }

    @Extension
    public static final class CloudActionFactory extends TransientActionFactory<Cloud> {
        @Override
        public Class<Cloud> type() {
            return Cloud.class;
        }

        @NotNull
        @Override
        public Collection<? extends Action> createFor(@NotNull Cloud target) {
            if (target instanceof KubernetesCloud) {
                return Collections.singleton(new PodTemplatesCloudAction());
            }
            return Collections.emptyList();
        }
    }
}
