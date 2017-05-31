package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.OfflineCause;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.csanchez.jenkins.plugins.kubernetes.SelfRegisteredSlaveRetentionStrategy.getPodName;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class SelfRegisteredSlaveRetentionStrategyTest {

    @Mock
    private Node node;

    @Mock
    private Computer computer;

    @Mock
    private KubernetesCloud kubernetesCloud;

    @Mock
    private KubernetesClient kubernetesClient;

    private SelfRegisteredSlaveRetentionStrategy unit = new SelfRegisteredSlaveRetentionStrategy("cloudName", "namespace", 0);

    @Before
    public void setUp() {
        unit = spy(unit);
        doReturn("nodeName").when(node).getNodeName();  // To have a better looking logging
        doNothing().when(unit).deletePod(anyString(), any(KubernetesClient.class));
    }

    @Test
    public void shouldGetPodName() throws Exception {
        assertEquals("pod-1234", getPodName("pod-1234"));
        assertEquals("pod-1234", getPodName("pod-1234-suffix"));
    }

    @Test
    public void shouldNotTerminateNodeIfItHasNoComputer() throws Exception {
        doReturn(null).when(unit).getNodeComputer(eq(node));
        assertThat(unit.canTerminate(node)).isFalse();
    }

    @Test
    public void shouldNotTerminateNodeIfNotKubeCloud() throws Exception {
        doReturn(computer).when(unit).getNodeComputer(eq(node));
        doReturn(mock(Cloud.class)).when(unit).getCloud();
        assertThat(unit.canTerminate(node)).isFalse();
    }

    @Test
    public void terminateNodeHappyPath() throws Exception {
        doReturn(computer).when(unit).getNodeComputer(eq(node));
        doReturn(kubernetesCloud).when(unit).getCloud();
        doReturn(kubernetesClient).when(kubernetesCloud).connect();
        unit.terminate(node);
        verifyDeletePodAttempts(1);
        verifyDisconnectComputerAttempts(1);
    }

    private void verifyDisconnectComputerAttempts(int expectedCount) {
        verify(computer, times(expectedCount)).disconnect(any(OfflineCause.class));
    }

    private void verifyDeletePodAttempts(int expectedCount) {
        verify(unit, times(expectedCount)).deletePod(anyString(), any(KubernetesClient.class));
    }
}