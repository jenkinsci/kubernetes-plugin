package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.EmptyStackException;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class NamespaceAction extends AbstractInvisibleRunAction2 implements RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(NamespaceAction.class.getName());

    NamespaceAction() {
        super();
    }

    @Deprecated
    public NamespaceAction(Run run) {
        setRun(run);
    }

    protected static void push(@NonNull Run<?, ?> run, @NonNull String item) throws IOException {
        AbstractInvisibleRunAction2.push(run, NamespaceAction.class, item);
    }

    @Deprecated
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
                action.stack.push(namespace);
                bc.commit();
            } finally {
                bc.abort();
            }
        }
    }

    @Deprecated
    @SuppressFBWarnings("DLS_DEAD_LOCAL_STORE")
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
                String namespace = action.stack.pop();
                bc.commit();
                return namespace;
            } finally {
                bc.abort();
                return null;
            }
        }
    }

    public String getNamespace() {
        try {
            return stack.peek();
        } catch (EmptyStackException e) {
            return null;
        }
    }
}
