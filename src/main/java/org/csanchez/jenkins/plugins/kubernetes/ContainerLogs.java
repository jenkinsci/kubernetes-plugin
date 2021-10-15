package org.csanchez.jenkins.plugins.kubernetes;

/**
 * Exception class used to convey container logs after a failure for information
 */
public class ContainerLogs extends Exception {
    public ContainerLogs() {
    }

    public ContainerLogs(String message) {
        super(message);
    }

    public ContainerLogs(String message, Throwable cause) {
        super(message, cause);
    }

    public ContainerLogs(Throwable cause) {
        super(cause);
    }

    public ContainerLogs(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
