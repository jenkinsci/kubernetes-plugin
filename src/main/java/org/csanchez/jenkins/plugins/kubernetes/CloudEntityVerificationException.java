package org.csanchez.jenkins.plugins.kubernetes;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class CloudEntityVerificationException extends RuntimeException {

    public CloudEntityVerificationException(String message) {
        super(message);
    }
}
