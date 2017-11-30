package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.util.ArrayList;
import java.util.List;

import hudson.model.Run;
import jenkins.model.RunAction2;

public class PodTemplateAction extends AbstractInvisibleRunAction2 implements RunAction2 {

    PodTemplateAction(Run<?, ?> run) {
        super(run);
    }

    @Override
    protected AbstractInvisibleRunAction2 createAction(Run<?, ?> run) {
        return new PodTemplateAction(run);
    }

    public List<String> getParentTemplateList() {
        synchronized (getRun()) {
            PodTemplateAction action = getRun().getAction(PodTemplateAction.class);
            if (action == null) {
                action = new PodTemplateAction(getRun());
                getRun().addAction(action);
            }
            return new ArrayList<>(action.stack);
        }
    }

    public String getParentTemplates() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String template : getParentTemplateList()) {
            if (first) {
                first = false;
            } else {
                sb.append(" ");
            }
            sb.append(template);

        }
        return sb.toString();
    }

}
