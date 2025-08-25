package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.AbortException;
import hudson.model.Node;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KubernetesNodeContextTest {

    @Mock
    private StepContext context;

    @Mock
    private KubernetesSlave slave;

    @Mock
    private KubernetesCloud cloud;

    @Mock
    private PodResource podResource;

    @BeforeEach
    void beforeEach() {
        when(slave.getKubernetesCloud()).thenReturn(cloud);
        when(slave.getPodName()).thenReturn("foo");
        when(slave.getNamespace()).thenReturn("bar");
    }

    @Test
    void createContext() throws Exception {
        when(cloud.getPodResource("bar", "foo")).thenReturn(podResource);
        when(context.get(Node.class)).thenReturn(slave);
        KubernetesNodeContext knc = new KubernetesNodeContext(context);
        assertEquals("foo", knc.getPodName());
        assertEquals("bar", knc.getNamespace());
        assertSame(slave, knc.getKubernetesSlave());
        assertSame(podResource, knc.getPodResource());
    }

    @Test
    void notKubernetesSlaveInstance() throws Exception {
        Node node = mock(Node.class);
        when(context.get(Node.class)).thenReturn(node);
        AbortException ae = assertThrows(AbortException.class, () -> new KubernetesNodeContext(context));
        assertTrue(ae.getMessage().startsWith("Node is not a Kubernetes node"));
    }
}
