package io.jenkins.plugins.kubernetes;

import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;

import java.io.PrintStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class TaskListenerEventWatcher implements Watcher<Event> {

    private static final Logger LOGGER = Logger.getLogger(TaskListenerEventWatcher.class.getName());

    private final String name;
    private final TaskListener listener;

    public TaskListenerEventWatcher(String name, TaskListener listener) {
        this.name = name;
        this.listener = listener;
    }

    private void listenerLog(Event event) {
        PrintStream logger = listener.getLogger();
        // Messages can have multiple lines
        String[] lines = event.getMessage().split("\n");
        for (String line : lines) {
            logger.printf("[%s][%s/%s][%s] %s%n", event.getType(), event.getInvolvedObject().getNamespace(), event.getInvolvedObject().getName(), event.getReason(), line);
        }
    }

    @Override
    public void eventReceived(Action action, Event event) {
        switch (event.getType()) {
            case "Normal":
                listenerLog(event);
                break;
            case "Warning":
                listenerLog(event);
                break;
            default:
                listenerLog(event);
                break;
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        String msg = String.format("%s onClose: %s", getClass().getSimpleName(), name);
        if (cause != null) {
            LOGGER.log(WARNING, msg, cause);
            cause.printStackTrace(listener.getLogger());
        } else {
            LOGGER.log(FINE, msg);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [name=" + name + "]";
    }

}
