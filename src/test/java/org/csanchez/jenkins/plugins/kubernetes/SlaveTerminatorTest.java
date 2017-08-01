package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Computer;
import hudson.model.Slave;
import hudson.slaves.OfflineCause;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class SlaveTerminatorTest {

    private static final String NODE_NAME = "nodeName";

    private static final String BAD_NODE = "badBadNode";

    private static final String NAMESPACE = "namespace";

    @Mock
    private Slave slave;

    @Mock
    private Computer computer;

    @Mock
    private KubernetesCloud cloud;

    @Mock
    private PodResource goodPodResource;

    @Mock
    private PodResource badPodResource;

    @Mock
    private NonNamespaceOperation nonNamespaceOperation;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private KubernetesClient client;

    @Spy
    @InjectMocks
    private SlaveTerminator unit;

    @Before
    public void setUp() {
        doReturn(NODE_NAME).when(slave).getNodeName();  // To have a better looking logging
    }

    @Test
    public void shouldHandleErrorsOnTerminate() throws Exception {
        doReturn(client).doThrow(new RuntimeException()).when(cloud).connect();
        doReturn(false).when(unit).deletePod(client, NAMESPACE, NODE_NAME);

        assertThat(unit.terminatePodSlave(slave, NAMESPACE)).isFalse();
        assertThat(unit.terminatePodSlave(slave, NAMESPACE)).isFalse();
    }

    @Test
    public void terminateNodeHappyPath() throws Exception {
        doReturn(computer).when(unit).getSlaveComputer(eq(slave));
        doReturn(true).when(unit).deletePod(client, NAMESPACE, NODE_NAME);
        doReturn(client).when(cloud).connect();
        assertThat(unit.terminatePodSlave(slave, NAMESPACE)).isTrue();
        verifyDeletePodAttempts(1);
        verifyDisconnectComputerAttempts(1);
    }

    @Test
    public void shouldReturnDeletedStatus() throws Exception {
        when(client.pods().inNamespace(NAMESPACE)).thenReturn(nonNamespaceOperation);
        when(nonNamespaceOperation.withName(NODE_NAME)).thenReturn(goodPodResource);
        when(goodPodResource.delete()).thenReturn(true);

        when(nonNamespaceOperation.withName(BAD_NODE)).thenReturn(badPodResource);
        when(badPodResource.delete()).thenReturn(false);

        assertThat(unit.deletePod(client, NAMESPACE, NODE_NAME)).isTrue();
        assertThat(unit.deletePod(client, NAMESPACE, BAD_NODE)).isFalse();
    }

    @Test
    public void shouldThrowExceptionOnMissingPod() throws Exception {
        when(client.pods().inNamespace(NAMESPACE)).thenReturn(nonNamespaceOperation);
        when(nonNamespaceOperation.withName(BAD_NODE)).thenReturn(badPodResource);
        when(badPodResource.delete()).thenReturn(null);

        assertThatThrownBy(() -> unit.deletePod(client, NAMESPACE, BAD_NODE))
                .isInstanceOf(KubernetesSlaveException.class)
                .hasMessageContaining(format("Pod %s was not found in namespace %s", BAD_NODE, NAMESPACE));
    }

    private void verifyDisconnectComputerAttempts(int expectedCount) {
        verify(computer, times(expectedCount)).disconnect(any(OfflineCause.class));
    }

    private void verifyDeletePodAttempts(int expectedCount) {
        verify(unit, times(expectedCount)).deletePod(eq(client), anyString(), anyString());
    }
}