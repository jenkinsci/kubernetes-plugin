package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;

import java.io.PrintStream;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

class TaskListenerEventWatcher implements Watcher<Event> {

    private static final Logger LOGGER = Logger.getLogger(TaskListenerEventWatcher.class.getName());

    private final String name;
    private final TaskListener listener;

    public TaskListenerEventWatcher(@NonNull String name, @NonNull TaskListener listener) {
        this.name = name;
        this.listener = listener;
    }

    @Override
    public void eventReceived(Action action, Event event) {
        // ignore bookmark actions
        // event may be null if Error action
        if (action != Action.BOOKMARK && event != null) {
            PrintStream logger = listener.getLogger();
            // Messages can have multiple lines
            String[] lines = event.getMessage().split("\n");
            for (String line : lines) {
                logger.printf("[%s][%s/%s][%s] %s%n", event.getType(), event.getInvolvedObject().getNamespace(), event.getInvolvedObject().getName(), event.getReason(), line);
            }
        }
    }

    @Override
    public void onClose(WatcherException cause) {
        String msg = String.format("%s onClose: %s", getClass().getSimpleName(), name);
        if (cause != null) {
            LOGGER.log(WARNING, msg, cause);
            Functions.printStackTrace(cause, listener.getLogger());
        } else {
            LOGGER.log(FINE, msg);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [name=" + name + "]";
    }

}
