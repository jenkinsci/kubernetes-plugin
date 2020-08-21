package org.csanchez.jenkins.plugins.kubernetes;

public class InvalidPodTemplateException extends RuntimeException {
    String reason;

    public InvalidPodTemplateException(String reason, String msg) {
        super(msg);
        this.reason = reason;
    }

    public String getReason() {
        return this.reason;
    }
}
