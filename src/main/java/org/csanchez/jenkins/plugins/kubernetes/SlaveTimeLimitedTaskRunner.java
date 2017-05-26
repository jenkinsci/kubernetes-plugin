package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Throwables;

import java.util.Optional;

import static java.lang.String.format;

class SlaveTimeLimitedTaskRunner {

    static final int DEFAULT_SLEEP_AFTER_FAILURE_IN_MILLIS = 1000;

    static SlaveOperationDetails performUntilTimeout(SlaveTimeLimitedTask callback, int timeoutInSeconds)
            throws SlaveOperationTimeoutException {
        return performUntilTimeout(callback, timeoutInSeconds, DEFAULT_SLEEP_AFTER_FAILURE_IN_MILLIS);
    }

    private static SlaveOperationDetails performUntilTimeout(SlaveTimeLimitedTask task, int timeoutInSeconds, int sleepAfterFailureInMillis)
            throws SlaveOperationTimeoutException {
        int timeoutMillis = timeoutInSeconds * 1000;
        final long start = System.currentTimeMillis();
        int attemptNo = 0;
        while (true) {
            Optional<SlaveOperationDetails> optionalOperationDetails = task.attemptToPerform(++attemptNo);
            if (optionalOperationDetails.isPresent()) {
                SlaveOperationDetails operationDetails = optionalOperationDetails.get();
                operationDetails.setSecondsSpent((int) getMillisSpentSinceStart(start) / 1000);
                return operationDetails;
            }
            if (getMillisSpentSinceStart(start) >= timeoutMillis) {
                throw new SlaveOperationTimeoutException(format("Operation timed out after %d milliseconds", timeoutMillis));
            }
            sleep(sleepAfterFailureInMillis);
        }
    }

    private static long getMillisSpentSinceStart(long start) {
        return System.currentTimeMillis() - start;
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
