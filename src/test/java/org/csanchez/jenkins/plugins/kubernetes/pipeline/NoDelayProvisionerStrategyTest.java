package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.ExtensionList;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.TestExtension;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationMode;

import javax.annotation.Nonnull;
import java.util.Collection;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NoDelayProvisionerStrategyTest extends AbstractKubernetesPipelineTest {
    @Mock
    CloudProvisioningListener cloudProvisioningListener;

    @Before
    public void setUp() throws Exception {
        CloudProvisionerListenerImpl instance = ExtensionList.lookupSingleton(CloudProvisionerListenerImpl.class);
        instance.setDelegate(cloudProvisioningListener);
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
        assertNotNull(createJobThenScheduleRun());
    }

    @TestExtension
    public static class CloudProvisionerListenerImpl extends CloudProvisioningListener {
        private CloudProvisioningListener delegate;

        public void setDelegate(CloudProvisioningListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onStarted(Cloud cloud, Label label, Collection<NodeProvisioner.PlannedNode> plannedNodes) {
            delegate.onStarted(cloud, label, plannedNodes);
        }

        @Override
        public void onComplete(NodeProvisioner.PlannedNode plannedNode, Node node) {
            delegate.onComplete(plannedNode, node);
        }

        @Override
        public void onCommit(@Nonnull NodeProvisioner.PlannedNode plannedNode, @Nonnull Node node) {
            delegate.onCommit(plannedNode, node);
        }

        @Override
        public void onFailure(NodeProvisioner.PlannedNode plannedNode, Throwable t) {
            delegate.onFailure(plannedNode, t);
        }

        @Override
        public void onRollback(@Nonnull NodeProvisioner.PlannedNode plannedNode, @Nonnull Node node, @Nonnull Throwable t) {
            delegate.onRollback(plannedNode, node, t);
        }

        @Override
        public CauseOfBlockage canProvision(Cloud cloud, Label label, int numExecutors) {
            return delegate.canProvision(cloud, label, numExecutors);
        }
    }

    @Test
    public void noDelayProvisionerCallsListener() throws Exception {
        when(cloudProvisioningListener.canProvision(any(Cloud.class), any(Label.class), anyInt())).thenReturn(null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        verify(cloudProvisioningListener, atLeastOnce()).onStarted(eq(cloud), any(), any());
        verify(cloudProvisioningListener, atLeastOnce()).canProvision(any(Cloud.class), any(Label.class), anyInt());

    }
}
