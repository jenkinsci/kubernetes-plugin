package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Computer;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.jspecify.annotations.NonNull;

public class EventsAction implements Action {
    private final Computer owner;

    public EventsAction(Computer owner) {
        this.owner = owner;
    }

    public Computer getOwner() {
        return owner;
    }

    @Override
    public String getIconFileName() {
        return "symbol-list";
    }

    @Override
    public String getDisplayName() {
        return "Events";
    }

    @Override
    public String getUrlName() {
        return "events";
    }

    @Extension
    public static final class EventsActionFactory extends TransientActionFactory<Computer> {

        @Override
        public Class<Computer> type() {
            return Computer.class;
        }

        @Override
        public @NonNull Collection<? extends Action> createFor(@NonNull Computer target) {
            if (!Jenkins.get().hasPermission(Jenkins.MANAGE)) {
                return List.of();
            }
            return List.of(new EventsAction(target));
        }
    }
}
