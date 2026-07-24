package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Action;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import org.jspecify.annotations.NonNull;

public final class EventsAction implements Action {
    private final KubernetesComputer owner;

    private EventsAction(KubernetesComputer owner) {
        this.owner = owner;
    }

    public KubernetesComputer getOwner() {
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
    public static final class EventsActionFactory extends TransientActionFactory<KubernetesComputer> {

        @Override
        public Class<KubernetesComputer> type() {
            return KubernetesComputer.class;
        }

        @Override
        public Class<? extends Action> actionType() {
            return EventsAction.class;
        }

        @Override
        public @NonNull Collection<? extends Action> createFor(@NonNull KubernetesComputer target) {
            if (!Jenkins.get().hasPermission(Jenkins.MANAGE)) {
                return List.of();
            }
            return List.of(new EventsAction(target));
        }
    }
}
