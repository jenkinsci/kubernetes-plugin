package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Slave;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class SlaveTimeLimitedTaskRunnerTest {

    @Mock
    private Slave slave;

    @Mock
    private SlaveSupplier slaveSupplier;

    @Test(timeout = 4500)
    public void testPerformUntilTimeout_timesOut() throws Exception {
        when(slaveSupplier.get()).thenReturn(null);
        assertThatExceptionOfType(SlaveOperationTimeoutException.class)
                .isThrownBy(() -> SlaveTimeLimitedTaskRunner.performUntilTimeout(attemptNumber -> sampleSlaveOperation(), 4));
        verify(slaveSupplier, times(4)).get();
    }

    @Test(timeout = 2000)
    public void testPerformUntilTimeout_toleratesFault() throws Exception {
        when(slaveSupplier.get()).thenReturn(null).thenReturn(slave);
        SlaveOperationDetails slaveOperationDetails =
                SlaveTimeLimitedTaskRunner.performUntilTimeout(attemptNumber -> sampleSlaveOperation(), 1, 300);
        assertSame(slave, slaveOperationDetails.getSlave());
        verify(slaveSupplier, atLeast(2)).get();
    }

    private Optional<SlaveOperationDetails> sampleSlaveOperation() {
        Slave slave = slaveSupplier.get();
        return slave == null ? Optional.empty() : Optional.of(new SlaveOperationDetails(slave));
    }

    private interface SlaveSupplier extends Supplier<Slave> {
        Slave get();
    }

}