package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import hudson.BulkChange;
import hudson.model.InvisibleAction;
import hudson.model.Run;

public class PodTemplateAction extends InvisibleAction {

    private final Stack<String> names = new Stack<>();
    private final Run run;


    PodTemplateAction(Run run) {
        this.run = run;
    }

    public void push(String template) throws IOException {
        synchronized (run) {
            BulkChange bc = new BulkChange(run);
            try {
                PodTemplateAction action = run.getAction(PodTemplateAction.class);
                if (action == null) {
                    action = new PodTemplateAction(run);
                    run.addAction(action);
                }
                action.names.push(template);
                bc.commit();
            } finally {
                bc.abort();
            }
        }
    }

    public String pop() throws IOException {
        synchronized (run) {
            BulkChange bc = new BulkChange(run);
            try {
                PodTemplateAction action = run.getAction(PodTemplateAction.class);
                if (action == null) {
                    action = new PodTemplateAction(run);
                    run.addAction(action);
                }
                String template = action.names.pop();
                bc.commit();
                return template;
            } finally {
                bc.abort();
                return null;
            }
        }
    }

    public List<String> getParentTemplateList() {
        synchronized (run) {
            PodTemplateAction action = run.getAction(PodTemplateAction.class);
            if (action == null) {
                action = new PodTemplateAction(run);
                run.addAction(action);
            }
            return new ArrayList<>(action.names);
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
