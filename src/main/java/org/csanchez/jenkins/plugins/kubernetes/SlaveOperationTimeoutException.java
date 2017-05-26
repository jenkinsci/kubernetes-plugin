package org.csanchez.jenkins.plugins.kubernetes;

/**
 */
class SlaveOperationTimeoutException extends Exception {

    public SlaveOperationTimeoutException(String message) {
        super(message);
    }

    public SlaveOperationTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public SlaveOperationTimeoutException(Throwable cause) {
        super(cause);
    }
}
