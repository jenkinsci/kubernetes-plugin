package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.AbortException;
import hudson.model.Node;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesNodeContextTest {

    @Mock
    private StepContext context;

    @Mock
    private KubernetesSlave slave;

    @Mock
    private KubernetesCloud cloud;

    @Mock
    private PodResource podResource;

    @Before
    public void setup() {
        when(slave.getKubernetesCloud()).thenReturn(cloud);
        when(slave.getPodName()).thenReturn("foo");
        when(slave.getNamespace()).thenReturn("bar");
    }

    @Test
    public void createContext() throws Exception {
        when(cloud.getPodResource("bar", "foo")).thenReturn(podResource);
        when(context.get(Node.class)).thenReturn(slave);
        KubernetesNodeContext knc = new KubernetesNodeContext(context);
        assertEquals("foo", knc.getPodName());
        assertEquals("bar", knc.getNamespace());
        assertSame(slave, knc.getKubernetesSlave());
        assertSame(podResource, knc.getPodResource());
    }

    @Test
    public void notKubernetesSlaveInstance() throws Exception {
        Node node = mock(Node.class);
        when(context.get(Node.class)).thenReturn(node);
        try {
            new KubernetesNodeContext(context);
            fail("expected abort exception");
        } catch (AbortException ae) {
            assertTrue(ae.getMessage().startsWith("Node is not a Kubernetes node"));
        }
    }
}
