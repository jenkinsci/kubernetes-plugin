package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;

import hudson.BulkChange;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class PodTemplateAction extends AbstractInvisibleRunAction2 implements RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(PodTemplateAction.class.getName());

    private final Stack<String> names = new Stack<>();

    PodTemplateAction(Run<?, ?> run) {
        setRun(run);
    }

    public void push(String template) throws IOException {
        if (getRun() == null) {
            LOGGER.warning("run is null, cannot push");
            return;
        }
        synchronized (getRun()) {
            BulkChange bc = new BulkChange(getRun());
            try {
                PodTemplateAction action = getRun().getAction(PodTemplateAction.class);
                if (action == null) {
                    action = new PodTemplateAction(getRun());
                    getRun().addAction(action);
                }
                action.names.push(template);
                bc.commit();
            } finally {
                bc.abort();
            }
        }
    }

    public String pop() throws IOException {
        if (getRun() == null) {
            LOGGER.warning("run is null, cannot pop");
            return null;
        }
        synchronized (getRun()) {
            BulkChange bc = new BulkChange(getRun());
            try {
                PodTemplateAction action = getRun().getAction(PodTemplateAction.class);
                if (action == null) {
                    action = new PodTemplateAction(getRun());
                    getRun().addAction(action);
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
        synchronized (getRun()) {
            PodTemplateAction action = getRun().getAction(PodTemplateAction.class);
            if (action == null) {
                action = new PodTemplateAction(getRun());
                getRun().addAction(action);
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
