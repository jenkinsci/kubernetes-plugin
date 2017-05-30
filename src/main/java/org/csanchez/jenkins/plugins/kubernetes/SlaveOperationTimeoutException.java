package org.csanchez.jenkins.plugins.kubernetes;

import static java.lang.String.format;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
class SlaveOperationTimeoutException extends Exception {

    private static final String DEFAULT_MESSAGE_PATTERN = "Operation timed out after %d seconds";

    public SlaveOperationTimeoutException(int secondsSpent) {
        super(format(DEFAULT_MESSAGE_PATTERN, secondsSpent));
    }

}
