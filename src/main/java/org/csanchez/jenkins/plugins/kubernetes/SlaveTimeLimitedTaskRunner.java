package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Throwables;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
class SlaveTimeLimitedTaskRunner {

    static final int DEFAULT_SLEEP_AFTER_FAILURE_IN_MILLIS = 1000;

    static SlaveOperationDetails performUntilTimeout(SlaveTimeLimitedTask callback, int timeoutInSeconds)
            throws SlaveOperationTimeoutException {
        return performUntilTimeout(callback, timeoutInSeconds, DEFAULT_SLEEP_AFTER_FAILURE_IN_MILLIS);
    }

    static SlaveOperationDetails performUntilTimeout(SlaveTimeLimitedTask task, int timeoutInSeconds, int sleepAfterFailureInMillis)
            throws SlaveOperationTimeoutException {
        final Instant start = Instant.now();
        int attemptNo = 0;
        while (true) {
            if (secondsPassedSince(start) >= timeoutInSeconds) {
                throw new SlaveOperationTimeoutException(timeoutInSeconds);
            }
            Optional<SlaveOperationDetails> optionalOperationDetails = task.attemptToPerform(++attemptNo);
            if (optionalOperationDetails.isPresent()) {
                SlaveOperationDetails operationDetails = optionalOperationDetails.get();
                operationDetails.setSecondsSpent(secondsPassedSince(start));
                return operationDetails;
            }
            sleep(sleepAfterFailureInMillis);
        }
    }

    private static int secondsPassedSince(Instant start) {
        return (int) ChronoUnit.SECONDS.between(start, Instant.now());
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }

}
