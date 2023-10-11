package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Action;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.TransientActionFactory;

@Extension
public class PodTemplateAction extends TransientActionFactory<KubernetesCloud> implements Action{
    
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

    @Override
    public Class<KubernetesCloud> type() {
        return KubernetesCloud.class;
    }

    @Override
    public Collection<? extends Action> createFor(KubernetesCloud target) {
    List<Action> actions = new ArrayList<>();
    actions.add(new PodTemplateAction());
    return actions;
    }
}
