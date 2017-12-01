package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import jenkins.model.RunAction2;

public class PodTemplateAction extends AbstractInvisibleRunAction2 implements RunAction2 {

    PodTemplateAction() {
        super();
    }

    @Deprecated
    PodTemplateAction(Run<?, ?> run) {
        super(run);
    }

    @Override
    @Deprecated
    protected AbstractInvisibleRunAction2 createAction(Run<?, ?> run) {
        return new PodTemplateAction(run);
    }

    protected static void push(@NonNull Run<?, ?> run, @NonNull String item) throws IOException {
        AbstractInvisibleRunAction2.push(run, PodTemplateAction.class, item);
    }

    public List<String> getParentTemplateList() {
        return new ArrayList<>(stack);
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
