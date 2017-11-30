package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.EmptyStackException;
import java.util.Stack;
import java.util.logging.Logger;

import hudson.BulkChange;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class NamespaceAction extends AbstractInvisibleRunAction2 implements RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(NamespaceAction.class.getName());

    private final Stack<String> namespaces = new Stack<>();

    public NamespaceAction(Run<?, ?> run) {
        setRun(run);
    }

    public void push(String namespace) throws IOException {
        if (getRun() == null) {
            LOGGER.warning("run is null, cannot push");
            return;
        }
        synchronized (getRun()) {
            BulkChange bc = new BulkChange(getRun());
            try {
                NamespaceAction action = getRun().getAction(NamespaceAction.class);
                if (action == null) {
                    action = new NamespaceAction(getRun());
                    getRun().addAction(action);
                }
                action.namespaces.push(namespace);
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
                NamespaceAction action = getRun().getAction(NamespaceAction.class);
                if (action == null) {
                    action = new NamespaceAction(getRun());
                    getRun().addAction(action);
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
        synchronized (getRun()) {
            NamespaceAction action = getRun().getAction(NamespaceAction.class);
            if (action == null) {
                action = new NamespaceAction(getRun());
                getRun().addAction(action);
            }
            try {
                return action.namespaces.peek();
            } catch (EmptyStackException e) {
                return null;
            }
        }
    }
}
