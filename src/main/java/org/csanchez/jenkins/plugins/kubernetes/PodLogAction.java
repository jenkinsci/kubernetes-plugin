package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.jspecify.annotations.NonNull;

public class PodLogAction implements Action {
    private final Computer owner;

    public PodLogAction(Computer owner) {
        this.owner = owner;
    }

    public Computer getOwner() {
        return owner;
    }

    @Override
    public String getIconFileName() {
        return "symbol-terminal";
    }

    @Override
    public String getDisplayName() {
        return "Pod Log";
    }

    @Override
    public String getUrlName() {
        return "podLog";
    }

    @Extension
    public static final class PodLogActionFactory extends TransientActionFactory<Computer> {

        @Override
        public Class<Computer> type() {
            return Computer.class;
        }

        @Override
        public @NonNull Collection<? extends Action> createFor(@NonNull Computer target) {
            if (!Jenkins.get().hasPermission(Jenkins.MANAGE)) {
                return List.of();
            }
            return List.of(new PodLogAction(target));
        }
    }
}
