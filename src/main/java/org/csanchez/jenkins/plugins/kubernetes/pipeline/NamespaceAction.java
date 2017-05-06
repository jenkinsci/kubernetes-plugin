package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;

import hudson.BulkChange;
import hudson.model.InvisibleAction;
import hudson.model.Run;

public class NamespaceAction extends InvisibleAction {

    private static final Logger LOGGER = Logger.getLogger(NamespaceAction.class.getName());

    private final Stack<String> namespaces = new Stack<>();
    private final Run run;


    public NamespaceAction(Run run) {
        this.run = run;
    }

    public void push(String namespace) throws IOException {
        if (run == null) {
            LOGGER.warning("run is null, cannot push");
            return;
        }
        synchronized (run) {
            BulkChange bc = new BulkChange(run);
            try {
                NamespaceAction action = run.getAction(NamespaceAction.class);
                if (action == null) {
                    action = new NamespaceAction(run);
                    run.addAction(action);
                }
                action.namespaces.push(namespace);
                bc.commit();
            } finally {
                bc.abort();
            }
        }
    }

    public String pop() throws IOException {
        if (run == null) {
            LOGGER.warning("run is null, cannot pop");
            return null;
        }
        synchronized (run) {
            BulkChange bc = new BulkChange(run);
            try {
                NamespaceAction action = run.getAction(NamespaceAction.class);
                if (action == null) {
                    action = new NamespaceAction(run);
                    run.addAction(action);
                }
                String namespace = action.namespaces.pop();
                bc.commit();
                return namespace;
            } finally {
                bc.abort();
                return null;
            }
        }
    }

    public String getNamespace() {
        synchronized (run) {
            NamespaceAction action = run.getAction(NamespaceAction.class);
            if (action == null) {
                action = new NamespaceAction(run);
                run.addAction(action);
            }
            try {
                return action.namespaces.peek();
            } catch (EmptyStackException e) {
                return null;
            }
        }
    }
}
