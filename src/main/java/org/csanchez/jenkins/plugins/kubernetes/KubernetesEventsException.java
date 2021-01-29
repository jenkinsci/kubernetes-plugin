package org.csanchez.jenkins.plugins.kubernetes;

import io.fabric8.kubernetes.api.model.Event;

import java.util.List;

/**
 * Prints
 */
public class KubernetesEventsException extends Exception {
    public KubernetesEventsException(List<Event> events) {
        super(toMessage(events));
    }

    private static String toMessage(List<Event> events) {
        StringBuilder sb = new StringBuilder("Events follow:\n");
        for (Event event : events) {
            String[] lines = event.getMessage().split("\n");
            for (String line : lines) {
                sb.append(String.format("[%s][%s/%s][%s] %s%n", event.getType(), event.getInvolvedObject().getNamespace(), event.getInvolvedObject().getName(), event.getReason(), line));
            }
        }
        return sb.toString();
    }
}
