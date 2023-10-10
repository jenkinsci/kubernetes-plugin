package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Action;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.TransientActionFactory;

public class PodTemplateAction extends TransientActionFactory<KubernetesCloud> implements Action{
    
    @Override
    public String getIconFileName() {
        return "symbol-copy";
    }

    @Override
    public String getDisplayName() {
        return "PodTemplates";
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
