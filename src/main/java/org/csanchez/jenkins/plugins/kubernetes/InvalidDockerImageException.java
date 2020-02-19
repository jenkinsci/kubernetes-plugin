package org.csanchez.jenkins.plugins.kubernetes;

public class InvalidDockerImageException extends RuntimeException {
    public InvalidDockerImageException(String msg) {
        super(msg);
    }
}
