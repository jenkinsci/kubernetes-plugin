package org.csanchez.jenkins.plugins.kubernetes;

import java.util.Optional;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
interface SlaveTimeLimitedTask {

    Optional<SlaveOperationDetails> attemptToPerform(int attemptNumber);

}
