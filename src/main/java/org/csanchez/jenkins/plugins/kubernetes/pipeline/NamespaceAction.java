package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.util.EmptyStackException;

import hudson.model.Run;
import jenkins.model.RunAction2;

public class NamespaceAction extends AbstractInvisibleRunAction2 implements RunAction2 {

    public NamespaceAction(Run<?, ?> run) {
        super(run);
    }

    public String getNamespace() {
        synchronized (getRun()) {
            NamespaceAction action = getRun().getAction(NamespaceAction.class);
            if (action == null) {
                action = new NamespaceAction(getRun());
                getRun().addAction(action);
            }
            try {
                return action.stack.peek();
            } catch (EmptyStackException e) {
                return null;
            }
        }
    }
}
