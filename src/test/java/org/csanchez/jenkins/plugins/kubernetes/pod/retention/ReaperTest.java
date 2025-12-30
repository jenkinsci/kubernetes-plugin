/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.utils.Utils;
import io.fabric8.mockwebserver.http.RecordedRequest;
import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.csanchez.jenkins.plugins.kubernetes.*;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@EnableKubernetesMockClient
class ReaperTest {

    private static final Long EVENT_WAIT_PERIOD_MS = 10L;

    private JenkinsRule j;

    private KubernetesMockServer server;
    private KubernetesClient client;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @AfterEach
    void afterEach() {
        KubernetesClientProvider.invalidateAll();
    }

    @Test
    void testMaybeActivate() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // add node that does not exist in k8s so it get's removed
        KubernetesSlave podNotRunning = addNode(cloud, "k8s-node-123", "k8s-node");
        assertEquals(1, j.jenkins.getNodes().size(), "node added to jenkins");

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // k8s node which no pod should be deleted on activation
        assertEquals(0, j.jenkins.getNodes().size(), "node removed from jenkins");

        // watch was created
        assertShouldBeWatching(r, cloud);

        kubeClientRequests()
                // expect pod not running to be removed
                .assertRequestCount("/api/v1/namespaces/foo/pods/k8s-node-123", 1)
                // watch to be created
                .assertRequestCountAtLeast(watchPodsPath, 1);

        // create new node to verify activate is not run again
        KubernetesSlave newNode = addNode(cloud, "new-123", "new");
        j.jenkins.addNode(newNode);
        assertEquals(1, j.jenkins.getNodes().size(), "node added to jenkins");
        // call again should not add any more calls
        r.maybeActivate();

        kubeClientRequests()
                // expect not to be called
                .assertRequestCount("/api/v1/namespaces/foo/pods/new-123", 0);
        assertEquals(1, j.jenkins.getNodes().size(), "node not removed from jenkins");
    }

    @Test
    void testWatchFailOnActivate() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // expect watch to be attempted
        kubeClientRequests()
                .assertRequestCountAtLeast("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true", 1);
        // watch failed to register
        assertShouldNotBeWatching(r, cloud);
    }

    @Test
    void testActivateOnNewComputer() throws Exception {
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // initiate reaper
        Reaper r = Reaper.getInstance();

        // add new cloud
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave n2 = addNode(cloud, "p1-123", "p1");
        TaskListener tl = mock(TaskListener.class);
        KubernetesComputer kc = new KubernetesComputer(n2);

        // should not be watching the newly created cloud at this point
        assertShouldNotBeWatching(r, cloud);

        // fire compute on-line event
        r.preLaunch(kc, tl);

        // expect new cloud registered
        assertShouldBeWatching(r, cloud);
        kubeClientRequests()
                .assertRequestCountAtLeast("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true", 1);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testReconnectOnNewComputer() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .once();
        // trigger HTTP_GONE status which should result in Watcher#onClose(Exception)
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(outdatedEvent())
                .done()
                .once();
        // after Gone error, should reconnect fresh
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(2).assertRequestCount(watchPodsPath, 2);

        // error status event should be filtered out
        getReaperListener().expectNoEvents();

        // wait until watch is removed
        System.out.println("Waiting for watch to be removed");
        assertShouldNotBeWatching(r, cloud);
        System.out.println("Watch removed");

        // launch computer
        KubernetesSlave n2 = addNode(cloud, "p1-123", "p1");
        TaskListener tl = mock(TaskListener.class);
        KubernetesComputer kc = new KubernetesComputer(n2);
        r.preLaunch(kc, tl);

        // should have started new watch
        System.out.println("Waiting for a new watch to be started");
        assertShouldBeWatching(r, cloud);
        System.out.println("Watch started");
    }

    private CapturingReaperListener getReaperListener() {
        return ExtensionList.lookupSingleton(CapturingReaperListener.class);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testAddWatchWhenCloudAdded() throws Exception {
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        String cloudName = "k8s";
        assertFalse(r.isWatchingCloud(cloudName), "should not be watching cloud");

        KubernetesCloud cloud = addCloud(cloudName, "foo");

        // invalidate client
        j.jenkins.clouds.add(cloud);

        // watch is added
        assertShouldBeWatching(r, cloud);
        kubeClientRequests().assertRequestCountAtLeast(watchPodsPath, 1);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testRemoveWatchWhenCloudRemoved() {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // should not be watching the newly created cloud at this point
        assertShouldBeWatching(r, cloud);

        // invalidate client
        j.jenkins.clouds.remove(cloud);

        // watch is removed
        // org.csanchez.jenkins.plugins.kubernetes.pod.retention.Reaper.CloudPodWatcher.onClose() is called in a
        // separate thread
        assertShouldNotBeWatching(r, cloud);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testReplaceWatchWhenCloudUpdated() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        Pod node123 = new PodBuilder()
                .withNewStatus()
                .endStatus()
                .withNewMetadata()
                .withName("node-123")
                .withNamespace("bar")
                .endMetadata()
                .build();

        String watchFooPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchFooPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        String watchBarPodsPath = "/api/v1/namespaces/bar/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchBarPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .done()
                .always();
        // don't remove pod on activate
        server.expect()
                .withPath("/api/v1/namespaces/bar/pods/node-123")
                .andReturn(200, node123)
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // should not be watching the newly created cloud at this point
        assertShouldBeWatching(r, cloud);

        // invalidate client
        cloud.setNamespace("bar");
        j.jenkins.save();

        KubernetesSlave node = addNode(cloud, "node-123", "node");

        // watch is still active
        assertShouldBeWatching(r, cloud);

        getReaperListener().expectEvent(Watcher.Action.MODIFIED, node);
        kubeClientRequests().assertRequestCountAtLeast(watchBarPodsPath, 1);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testStopWatchingOnCloseException() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .once();
        // trigger HTTP_GONE status which should result in Watcher#onClose(Exception)
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(outdatedEvent())
                .done()
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(2).assertRequestCount(watchPodsPath, 2);

        // error status event should be filtered out
        getReaperListener().expectNoEvents();

        // watch is removed
        assertShouldNotBeWatching(r, cloud);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testKeepWatchingOnKubernetesApiServerError() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(errorEvent())
                .done()
                .once();
        // trigger error action event
        server.expect().withPath(watchPodsPath).andReturnChunked(500).once();
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .once();
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();
        server.expect().withPath(watchPodsPath).andReturnChunked(200).always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(3).assertRequestCount(watchPodsPath, 3);

        // error status event should be filtered out
        getReaperListener().expectNoEvents();

        // watch is still active
        assertShouldBeWatching(r, cloud);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testKeepWatchingOnStatusWatchEvent() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath).andReturnChunked(200).once();
        Status status = new Status();
        status.setStatus("Unknown");
        status.setReason("Some reason");
        status.setCode(200);
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .immediately()
                .andEmit(new WatchEvent(status, "ERROR"))
                .done()
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(2).assertRequestCount(watchPodsPath, 2);

        // error status event should be filtered out
        getReaperListener().expectNoEvents();

        // watch is still active
        assertShouldBeWatching(r, cloud);
    }

    @Test
    void testCloseWatchersOnShutdown() {
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";

        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // add more clouds to make sure they are all closed
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesCloud cloud2 = addCloud("c2", "foo");
        KubernetesCloud cloud3 = addCloud("c3", "foo");

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // watching cloud
        assertShouldBeWatching(r, cloud, cloud2, cloud3);

        // trigger shutdown listener
        new Reaper.ReaperShutdownListener().onBeforeShutdown();

        // watchers removed
        assertShouldNotBeWatching(r, cloud, cloud2, cloud3);
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testDeleteNodeOnPodDelete() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = createPod(node);

        server.expect()
                .withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "DELETED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
                .always();
        // don't remove pod on activate
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods/node-123")
                .andReturn(200, node123)
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals(1, j.jenkins.getNodes().size(), "jenkins nodes");

        // wait for the delete event to be processed
        waitForKubeClientRequests(6)
                .assertRequestCountAtLeast("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true", 3);

        // verify listener got notified
        getReaperListener().expectEvent(Watcher.Action.DELETED, node);

        // verify computer disconnected with offline cause
        verify(node.getComputer()).disconnect(isA(PodOfflineCause.class));

        // expect node to be removed
        assertEquals(0, j.jenkins.getNodes().size(), "jenkins nodes");
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testTerminateAgentOnContainerTerminated() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = withContainerStatusTerminated(createPod(node));

        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
                .always();
        // don't remove pod on activate
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods/node-123")
                .andReturn(200, node123)
                .once();
        // Get logs
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods?fieldSelector=" + Utils.toUrlEncoded("metadata.name=node-123"))
                .andReturn(
                        200,
                        new PodListBuilder()
                                .withNewMetadata()
                                .endMetadata()
                                .withItems(node123)
                                .build())
                .always();
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods/node-123/log?pretty=false&tailLines=30")
                .andReturn(200, "some log")
                .always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals(1, j.jenkins.getNodes().size(), "jenkins nodes");

        // verify listener got notified
        getReaperListener().expectEvent(Watcher.Action.MODIFIED, node);

        // expect node to be terminated
        verify(node, atLeastOnce()).terminate();
        // verify computer disconnected with offline cause
        verify(node.getComputer(), atLeastOnce()).disconnect(isA(PodOfflineCause.class));
        // verify node is still registered (will be removed when pod deleted)
        assertEquals(1, j.jenkins.getNodes().size(), "jenkins nodes");
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testTerminateAgentOnPodFailed() throws Exception {
        System.out.println(server.getPort());
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = createPod(node);
        node123.getStatus().setPhase("Failed");

        server.expect()
                .withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
                .always();
        // don't remove pod on activate
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods/node-123")
                .andReturn(200, node123)
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals(1, j.jenkins.getNodes().size(), "jenkins nodes");

        // verify listener got notified
        getReaperListener().expectEvent(Watcher.Action.MODIFIED, node);

        // expect node to be terminated
        verify(node, atLeastOnce()).terminate();
        // verify computer disconnected with offline cause
        verify(node.getComputer()).disconnect(isA(PodOfflineCause.class));
        // verify node is still registered (will be removed when pod deleted)
        assertEquals(1, j.jenkins.getNodes().size(), "jenkins nodes");
    }

    @Test
    @Timeout(value = 10_000, unit = TimeUnit.MILLISECONDS)
    void testTerminateAgentOnImagePullBackoff() throws Exception {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = withContainerImagePullBackoff(createPod(node));
        Reaper.TerminateAgentOnImagePullBackOff.BACKOFF_EVENTS_LIMIT = 2;

        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect()
                .withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
                .always();
        // don't remove pod on activate
        server.expect()
                .withPath("/api/v1/namespaces/foo/pods/node-123")
                .andReturn(200, node123)
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals(1, j.jenkins.getNodes().size(), "jenkins nodes");

        // wait for the delete event to be processed
        waitForKubeClientRequests(6).assertRequestCountAtLeast(watchPodsPath, 3);

        // verify listener got notified
        getReaperListener().expectEvent(Watcher.Action.MODIFIED, node);

        // expect node to be terminated
        verify(node, atLeastOnce()).terminate();
        // verify computer disconnected with offline cause
        verify(node.getComputer(), atLeastOnce()).disconnect(isA(PodOfflineCause.class));
        // verify node is still registered (will be removed when pod deleted)
        assertEquals(1, j.jenkins.getNodes().size(), "jenkins nodes");
    }

    private Pod withContainerImagePullBackoff(Pod pod) {
        ContainerStatus status = new ContainerStatusBuilder()
                .withNewState()
                .withNewWaiting("something Back-off pulling image something", "ImagePullBackoff")
                .endState()
                .build();
        pod.getStatus().getContainerStatuses().add(status);
        return pod;
    }

    private Pod withContainerStatusTerminated(Pod pod) {
        PodStatus podStatus = pod.getStatus();
        podStatus
                .getConditions()
                .add(new PodConditionBuilder()
                        .withType("Ready")
                        .withStatus("True")
                        .build());
        ContainerStatus status = new ContainerStatusBuilder()
                .withNewState()
                .withNewTerminated()
                .withExitCode(123)
                .withReason("because")
                .withContainerID("foo")
                .endTerminated()
                .endState()
                .build();
        podStatus.getContainerStatuses().add(status);
        return pod;
    }

    private Pod createPod(KubernetesSlave node) {
        return new PodBuilder()
                .withNewMetadata()
                .withName(node.getPodName())
                .withNamespace(node.getNamespace())
                .withUid(UUID.randomUUID().toString())
                .endMetadata()
                .withNewSpec()
                .endSpec()
                .withNewStatus()
                .endStatus()
                .build();
    }

    private KubernetesSlave addNode(KubernetesCloud cld, String podName, String nodeName) throws Exception {
        KubernetesSlave node = mock(KubernetesSlave.class);
        when(node.getNodeName()).thenReturn(nodeName);
        when(node.getNamespace()).thenReturn(cld.getNamespace());
        when(node.getPodName()).thenReturn(podName);
        when(node.getKubernetesCloud()).thenReturn(cld);
        when(node.getCloudName()).thenReturn(cld.name);
        when(node.getNumExecutors()).thenReturn(1);
        PodTemplate podTemplate = new PodTemplate();
        when(node.getTemplate()).thenReturn(podTemplate);
        when(node.getRunListener()).thenReturn(StreamTaskListener.fromStderr());
        ComputerLauncher launcher = mock(ComputerLauncher.class);
        when(node.getLauncher()).thenReturn(launcher);
        KubernetesComputer computer = mock(KubernetesComputer.class);
        when(node.getComputer()).thenReturn(computer);
        j.jenkins.addNode(node);
        return node;
    }

    private KubernetesCloud addCloud(String name, String namespace) {
        KubernetesCloud c = new KubernetesCloud(name);
        c.setServerUrl(client.getMasterUrl().toString());
        c.setNamespace(namespace);
        c.setSkipTlsVerify(true);
        j.jenkins.clouds.add(c);
        return c;
    }

    /**
     * Get all the requests made to the kube client so far. Drains the captured requests. Next
     * call to this method will only return newly captured requests.
     * @return captured kube client requests so far
     * @throws InterruptedException interrupted exception
     */
    private CapturedRequests kubeClientRequests() throws Exception {
        int count = server.getRequestCount();
        List<RecordedRequest> requests = new LinkedList<>();
        while (count-- > 0) {
            RecordedRequest rr = server.takeRequest(1, TimeUnit.SECONDS);
            if (rr != null) {
                requests.add(rr);
            }
        }
        return new CapturedRequests(requests);
    }

    /**
     * Wait until the specified number of kube client requests are captured.
     * @param count number of requests to wait for
     * @return captured requests
     * @throws InterruptedException interrupted exception
     */
    private CapturedRequests waitForKubeClientRequests(int count) throws Exception {
        List<RecordedRequest> requests = new LinkedList<>();
        while (count-- > 0) {
            requests.add(server.takeRequest());
        }
        return new CapturedRequests(requests);
    }

    private static class CapturedRequests {

        private final Map<String, Long> countByPath;

        CapturedRequests(List<RecordedRequest> requests) {
            this.countByPath =
                    requests.stream().collect(Collectors.groupingBy(RecordedRequest::getPath, Collectors.counting()));
        }

        CapturedRequests assertRequestCount(String path, long count) {
            assertEquals(count, (long) countByPath.getOrDefault(path, 0L), path + " count");
            return this;
        }

        CapturedRequests assertRequestCountAtLeast(String path, long count) {
            assertThat(path + " count at least", countByPath.getOrDefault(path, 0L), greaterThanOrEqualTo(count));
            return this;
        }
    }

    @TestExtension
    public static class CapturingReaperListener implements Reaper.Listener {

        private final List<ReaperListenerWatchEvent> capturedEvents = new CopyOnWriteArrayList<>();

        private static final Logger LOGGER = Logger.getLogger(CapturingReaperListener.class.getName());

        @Override
        public synchronized void onEvent(
                @NonNull Watcher.Action action,
                @NonNull KubernetesSlave node,
                @NonNull Pod pod,
                @NonNull Set<String> terminationReasons) {
            LOGGER.info(this + " capturing event: " + action + " for node: " + node + " pod: " + pod);
            capturedEvents.add(new ReaperListenerWatchEvent(action, node, pod));
        }

        private static class ReaperListenerWatchEventMatcher extends TypeSafeMatcher<ReaperListenerWatchEvent> {
            private final Watcher.Action action;
            private final KubernetesSlave node;

            public ReaperListenerWatchEventMatcher(Watcher.Action action, KubernetesSlave node) {
                this.action = action;
                this.node = node;
            }

            @Override
            protected boolean matchesSafely(ReaperListenerWatchEvent event) {
                return event.action == action && event.node == node;
            }

            @Override
            public void describeTo(Description description) {
                description
                        .appendText("event with action ")
                        .appendValue(action)
                        .appendText(" and node ")
                        .appendValue(node);
            }

            @Override
            protected void describeMismatchSafely(ReaperListenerWatchEvent item, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(item);
            }
        }

        public void expectEvent(Watcher.Action action, KubernetesSlave node) {
            await().until(
                            () -> capturedEvents,
                            hasItem(new CapturingReaperListener.ReaperListenerWatchEventMatcher(action, node)));
        }

        /**
         * Expect not event to have been received.
         */
        public void expectNoEvents() {
            assertThat("no watcher events", capturedEvents, empty());
        }
    }

    private record ReaperListenerWatchEvent(Watcher.Action action, KubernetesSlave node, Pod pod) {}

    private static WatchEvent outdatedEvent() {
        return new WatchEventBuilder()
                .withType(Watcher.Action.ERROR.name())
                .withType(Watcher.Action.ERROR.name())
                .withObject(new StatusBuilder()
                        .withCode(HttpURLConnection.HTTP_GONE)
                        .withMessage(
                                "410: The event in requested index is outdated and cleared (the requested history has been cleared [3/1]) [2]")
                        .build())
                .build();
    }

    private static WatchEvent errorEvent() {
        return new WatchEventBuilder()
                .withType(Watcher.Action.ERROR.name())
                .withObject(new StatusBuilder()
                        .withCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .withMessage("500: Internal error")
                        .build())
                .build();
    }

    private static void assertShouldBeWatching(Reaper r, KubernetesCloud... clouds) {
        for (KubernetesCloud cloud : clouds) {
            await("should be watching cloud " + cloud.name).until(() -> r.isWatchingCloud(cloud.name));
        }
    }

    private static void assertShouldNotBeWatching(Reaper r, KubernetesCloud... clouds) {
        for (KubernetesCloud cloud : clouds) {
            await("should not be watching cloud " + cloud.name).until(() -> !r.isWatchingCloud(cloud.name));
        }
    }
}
