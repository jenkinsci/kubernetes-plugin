package org.csanchez.jenkins.plugins.kubernetes;

import java.util.Optional;

interface SlaveTimeLimitedTask {

    Optional<SlaveOperationDetails> attemptToPerform(int attemptNumber);

}
