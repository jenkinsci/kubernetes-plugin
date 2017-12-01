package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.EmptyStackException;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class NamespaceAction extends AbstractInvisibleRunAction2 implements RunAction2 {

    NamespaceAction() {
        super();
    }

    @Deprecated
    public NamespaceAction(Run<?, ?> run) {
        super(run);
    }

    @Override
    @Deprecated
    protected AbstractInvisibleRunAction2 createAction(Run<?, ?> run) {
        return new NamespaceAction(run);
    }

    protected static void push(@NonNull Run<?, ?> run, @NonNull String item) throws IOException {
        AbstractInvisibleRunAction2.push(run, NamespaceAction.class, item);
    }

    public String getNamespace() {
        try {
            return stack.peek();
        } catch (EmptyStackException e) {
            return null;
        }
    }
}
