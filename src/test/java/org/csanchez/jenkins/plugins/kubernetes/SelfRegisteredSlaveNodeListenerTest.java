package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Node;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class SelfRegisteredSlaveNodeListenerTest {

    @Spy
    private SelfRegisteredSlaveNodeListener unit;

    @Mock
    private Node simpleNode;

    @Mock
    private AbstractCloudSlave cloudSlave;

    @Before
    public void setUp() throws IOException {
        doNothing().when(unit).kill(any(SelfRegisteredSlaveRetentionStrategy.class), any(Node.class));
    }

    @Test
    public void shouldKillOnlySelfRegisteredSlaves() throws Exception {
        unit.onDeleted(simpleNode);
        verify(unit, never()).kill(any(SelfRegisteredSlaveRetentionStrategy.class), any(Node.class));

        unit.onDeleted(cloudSlave);
        verify(unit, never()).kill(any(SelfRegisteredSlaveRetentionStrategy.class), any(Node.class));

        when(cloudSlave.getRetentionStrategy()).thenReturn(new CloudRetentionStrategy(0));
        unit.onDeleted(cloudSlave);
        verify(unit, never()).kill(any(SelfRegisteredSlaveRetentionStrategy.class), any(Node.class));

        SelfRegisteredSlaveRetentionStrategy strategy = new SelfRegisteredSlaveRetentionStrategy("strategy", null, null, 0);
        when(cloudSlave.getRetentionStrategy()).thenReturn(strategy);
        unit.onDeleted(cloudSlave);
        verify(unit).kill(strategy, cloudSlave);
    }

}