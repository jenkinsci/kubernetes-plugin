package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Label;
import hudson.model.Slave;
import hudson.slaves.SlaveComputer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class SelfRegisteringSlaveCallbackTest {

    private static final String POD_ID = "podId";
    
    private static final String NAMESPACE = "namespace";

    private static final String SLAVE_NAME = "mySlave";

    @Mock
    private KubernetesCloud cloud;

    @Mock
    private Slave slave;

    @Mock
    private SlaveComputer computer;

    @Mock
    private Pod pod;

    @Mock
    private PodStatus podStatus;

    @Mock
    private Jenkins jenkins;

    @Mock
    private PodTemplate podTemplate;

    private KubernetesCloud.SelfRegisteringSlaveCallback unit;

    @Before
    public void init() throws Exception {
        unit = new KubernetesCloud("cloudName").new SelfRegisteringSlaveCallback(cloud, podTemplate, mock(Label.class));
        unit = spy(unit);
        doReturn(jenkins).when(unit).jenkins();
        doReturn(SLAVE_NAME).when(slave).getDisplayName();
    }

    @Test
    public void waitForSlaveToConnect() throws Exception {
        doReturn(3).when(podTemplate).getSlaveConnectTimeout();
        // First don't find, then find
        doReturn(null).doReturn(slave).when(jenkins).getNode(POD_ID);

        SlaveOperationDetails slaveOperationDetails = unit.waitForSlaveToConnect(POD_ID, NAMESPACE);
        Slave slave = slaveOperationDetails.getSlave();
        assertThat(slave).isNotNull();
        assertThat(slave.getDisplayName()).isEqualTo(SLAVE_NAME);

        verify(unit, times(2)).findSelfRegisteredSlaveByPodId(POD_ID);
    }

}