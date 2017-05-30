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

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud.SimpleProvisioningCallback.NODE_WAS_DELETED_COMPUTER_IS_NULL_MSG;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class SimpleProvisioningCallbackTest {

    private static final String POD_ID = "podId";
    private static final String NAMESPACE = "namespace";

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

    private KubernetesCloud.SimpleProvisioningCallback unit;
    private int slaveConnectTimeout = 5;

    @Before
    public void init() {
        doReturn(podStatus).when(pod).getStatus();
        doReturn(computer).when(slave).getComputer();
        doReturn(slaveConnectTimeout).when(podTemplate).getSlaveConnectTimeout();

        unit = new KubernetesCloud("cloudName").new SimpleProvisioningCallback(cloud, podTemplate, mock(Label.class));
        unit = spy(unit);
        doReturn(jenkins).when(unit).jenkins();
        doReturn(pod).when(unit).getPodByNamespaceAndPodId(eq(NAMESPACE), eq(POD_ID));
        doNothing().when(unit).logLastSlaveContainerLines(eq(pod), eq(POD_ID));
    }

    @Test
    public void waitForSlaveToComeOnline_nodeDeleted() throws Exception {
        doReturn(null).when(jenkins).getNode(POD_ID);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> unit.waitForSlaveToComeOnline(POD_ID, NAMESPACE))
                .withMessage(NODE_WAS_DELETED_COMPUTER_IS_NULL_MSG);
    }

    @Test
    public void waitForSlaveToComeOnline_totallyNotOnline() throws Exception {
        doReturn(slave).when(jenkins).getNode(POD_ID);
        doReturn(true).when(computer).isOffline();
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> unit.waitForSlaveToComeOnline(POD_ID, NAMESPACE))
                .withMessageStartingWith(format("Slave is not connected and online after %d seconds", slaveConnectTimeout));
    }

    @Test
    public void waitForSlaveToComeOnline_happyPath() throws Exception {
        doReturn(slave).when(jenkins).getNode(POD_ID);
        doReturn(true).doReturn(true).doReturn(false).when(computer).isOffline();
        SlaveOperationDetails result = unit.waitForSlaveToComeOnline(POD_ID, NAMESPACE);
        assertEquals(slave, result.getSlave());
    }

}