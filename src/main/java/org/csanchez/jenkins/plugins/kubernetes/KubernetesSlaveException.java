package org.csanchez.jenkins.plugins.kubernetes;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class KubernetesSlaveException extends RuntimeException {

    public KubernetesSlaveException(String message) {
        super(message);
    }
}
