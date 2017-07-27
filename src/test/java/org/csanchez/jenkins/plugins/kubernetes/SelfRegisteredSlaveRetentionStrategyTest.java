package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Slave;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class SelfRegisteredSlaveRetentionStrategyTest {

    private static final String NODE_NAME = "nodeName";

    @Mock
    private Slave slave;

    @Mock
    private KubernetesCloud kubernetesCloud;

    private SelfRegisteredSlaveRetentionStrategy unit = new SelfRegisteredSlaveRetentionStrategy("cloudName", "namespace", NODE_NAME, 0);

    @Before
    public void setUp() throws Exception {
        unit = spy(unit);
        doReturn(NODE_NAME).when(slave).getNodeName();  // To have a better looking logging
        doReturn(kubernetesCloud).when(unit).getCloud();
        doNothing().when(unit).checkSlaveComputer(slave);
    }

    @Test
    public void shouldNotTerminateIfVerificationFails() throws Exception {
        doThrow(new CloudEntityVerificationException("")).doNothing().when(unit).checkCloudExistence();
        assertThat(unit.canTerminate(slave)).isFalse();
        assertThat(unit.canTerminate(slave)).isTrue();
    }

    @Test
    public void shouldCheckIfSameSlave() throws Exception {
        Slave differentSlave = mock(Slave.class);
        when(differentSlave.getNodeName()).thenReturn("anotherNode");
        assertThat(unit.canTerminate(differentSlave)).isFalse();
    }
}