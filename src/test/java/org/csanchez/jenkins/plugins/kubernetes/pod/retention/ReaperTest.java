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


import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import jenkins.model.Jenkins;
import okhttp3.mockwebserver.RecordedRequest;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.*;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.jvnet.hudson.test.JenkinsRule;

public class ReaperTest {

    private static final Long EVENT_WAIT_PERIOD_MS = 10L;

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public KubernetesServer server = new KubernetesServer();

    @Rule
    public CapturingReaperListener listener = new CapturingReaperListener();

    @After
    public void tearDown() {
        KubernetesClientProvider.invalidateAll();
    }

    @Test
    public void testMaybeActivate() throws IOException, InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // add node that does not exist in k8s so it get's removed
        KubernetesSlave podNotRunning = addNode(cloud, "k8s-node-123", "k8s-node");
        assertEquals("node added to jenkins", j.jenkins.getNodes().size(), 1);

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // k8s node which no pod should be deleted on activation
        assertEquals("node removed from jenkins", j.jenkins.getNodes().size(), 0);

        // watch was created
        assertTrue(r.isWatchingCloud(cloud.name));

        kubeClientRequests()
                // expect pod not running to be removed
                .assertRequestCount("/api/v1/namespaces/foo/pods/k8s-node-123", 1)
                // watch to be created
                .assertRequestCountAtLeast(watchPodsPath, 1);

        // create new node to verify activate is not run again
        KubernetesSlave newNode = addNode(cloud, "new-123", "new");
        j.jenkins.addNode(newNode);
        assertEquals("node added to jenkins", j.jenkins.getNodes().size(), 1);
        // call again should not add any more calls
        r.maybeActivate();

        kubeClientRequests()
                // expect not to be called
                .assertRequestCount("/api/v1/namespaces/foo/pods/new-123", 0);
        assertEquals("node not removed from jenkins", j.jenkins.getNodes().size(), 1);
    }

    @Test
    public void testWatchFailOnActivate() throws IOException, InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // expect watch to be attempted
        kubeClientRequests()
                .assertRequestCountAtLeast("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true", 1);
        // watch failed to register
        assertFalse(r.isWatchingCloud(cloud.name));
    }

    @Test
    public void testActivateOnNewComputer() throws IOException, InterruptedException {
        server.expect().withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
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
        assertFalse("should not be watching cloud", r.isWatchingCloud(cloud.name));

        // fire compute on-line event
        r.preLaunch(kc, tl);

        // expect new cloud registered
        while (!r.isWatchingCloud(cloud.name)) {
            Thread.sleep(100);
        }
        kubeClientRequests()
                .assertRequestCountAtLeast("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true", 1);
    }

    @Test(timeout = 10_000)
    public void testReconnectOnNewComputer() throws InterruptedException, IOException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .once();
        // trigger HTTP_GONE status which should result in Watcher#onClose(Exception)
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(outdatedEvent())
                .done()
                .once();
        // after Gone error, should reconnect fresh
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(2)
                .assertRequestCount(watchPodsPath, 2);

        // error status event should be filtered out
        listener.expectNoEvents();

        // wait until watch is removed
        System.out.println("Waiting for watch to be removed");
        while (r.isWatchingCloud(cloud.name)) {
            Thread.sleep(250L);
        }
        System.out.println("Watch removed");

        // launch computer
        KubernetesSlave n2 = addNode(cloud, "p1-123", "p1");
        TaskListener tl = mock(TaskListener.class);
        KubernetesComputer kc = new KubernetesComputer(n2);
        r.preLaunch(kc, tl);

        // should have started new watch
        System.out.println("Waiting for a new watch to be started");
        while (!r.isWatchingCloud(cloud.name)) {
            Thread.sleep(100);
        }
        System.out.println("Watch started");
    }

    @Test(timeout = 10_000)
    public void testAddWatchWhenCloudAdded() throws InterruptedException, IOException {
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        String cloudName = "k8s";
        assertFalse("should not be watching cloud", r.isWatchingCloud(cloudName));

        KubernetesCloud cloud = addCloud(cloudName, "foo");

        // invalidate client
        j.jenkins.clouds.add(cloud);

        // watch is added
        assertTrue("should be watching cloud", r.isWatchingCloud(cloud.name));
        kubeClientRequests()
                .assertRequestCountAtLeast(watchPodsPath, 1);
    }

    @Test(timeout = 10_000)
    public void testRemoveWatchWhenCloudRemoved() throws InterruptedException, IOException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // should not be watching the newly created cloud at this point
        assertTrue("should be watching cloud", r.isWatchingCloud(cloud.name));

        // invalidate client
        j.jenkins.clouds.remove(cloud);

        // watch is removed
        assertFalse("should not be watching cloud", r.isWatchingCloud(cloud.name));
    }

    @Test(timeout = 10_000)
    public void testReplaceWatchWhenCloudUpdated() throws InterruptedException, IOException {
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
        server.expect().withPath(watchFooPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();

        String watchBarPodsPath = "/api/v1/namespaces/bar/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchBarPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .done()
                .always();
        // don't remove pod on activate
        server.expect().withPath("/api/v1/namespaces/bar/pods/node-123").andReturn(200, node123).once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // should not be watching the newly created cloud at this point
        assertTrue("should be watching cloud", r.isWatchingCloud(cloud.name));

        // invalidate client
        cloud.setNamespace("bar");
        j.jenkins.save();

        KubernetesSlave node = addNode(cloud, "node-123", "node");

        // watch is still active
        assertTrue("should be watching cloud", r.isWatchingCloud(cloud.name));

        listener.waitForEvents()
                .expectEvent(Watcher.Action.MODIFIED, node);
        kubeClientRequests()
                .assertRequestCountAtLeast(watchBarPodsPath, 1);
    }

    @Test(timeout = 10_000)
    public void testStopWatchingOnCloseException() throws InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .once();
        // trigger HTTP_GONE status which should result in Watcher#onClose(Exception)
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(outdatedEvent())
                .done()
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(2)
                .assertRequestCount(watchPodsPath, 2);

        // error status event should be filtered out
        listener.expectNoEvents();

        // watch is removed
        while (r.isWatchingCloud(cloud.name)) {
            Thread.sleep(250L);
        }
        assertFalse(r.isWatchingCloud(cloud.name));
    }

    @Test(timeout = 10_000)
    public void testKeepWatchingOnKubernetesApiServerError() throws InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(errorEvent())
                .done()
                .once();
        // trigger error action event
        server.expect().withPath(watchPodsPath).andReturnChunked(500).once();
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .once();
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .done()
                .always();
        server.expect().withPath(watchPodsPath).andReturnChunked(200).always();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(3)
                .assertRequestCount(watchPodsPath, 3);

        // error status event should be filtered out
        listener.expectNoEvents();

        // watch is still active
        assertTrue(r.isWatchingCloud(cloud.name));
    }

    @Test(timeout = 10_000)
    public void testKeepWatchingOnStatusWatchEvent() throws InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath).andReturnChunked(200).once();
        Status status = new Status();
        status.setStatus("Unknown");
        status.setReason("Some reason");
        status.setCode(200);
        server.expect().withPath(watchPodsPath)
                .andReturnChunked(200, new WatchEvent(status, "ERROR"))
                .once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        waitForKubeClientRequests(2)
                .assertRequestCount(watchPodsPath, 2);

        // error status event should be filtered out
        listener.expectNoEvents();

        // watch is still active
        assertTrue(r.isWatchingCloud(cloud.name));
    }

    @Test
    public void testCloseWatchersOnShutdown() throws InterruptedException {
        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";

        server.expect().withPath(watchPodsPath)
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
        assertTrue(r.isWatchingCloud(cloud.name));
        assertTrue(r.isWatchingCloud(cloud2.name));
        assertTrue(r.isWatchingCloud(cloud3.name));

        // trigger shutdown listener
        new Reaper.ReaperShutdownListener().onBeforeShutdown();

        // watchers removed
        assertFalse(r.isWatchingCloud(cloud.name));
        assertFalse(r.isWatchingCloud(cloud2.name));
        assertFalse(r.isWatchingCloud(cloud3.name));
    }

    @Test(timeout = 10_000)
    public void testDeleteNodeOnPodDelete() throws IOException, InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = createPod(node);

        server.expect().withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "DELETED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
        .always();
        // don't remove pod on activate
        server.expect().withPath("/api/v1/namespaces/foo/pods/node-123").andReturn(200, node123).once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 1);

        // wait for the delete event to be processed
        waitForKubeClientRequests(6)
                .assertRequestCountAtLeast("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true", 3);

        // verify listener got notified
        listener.expectEvent(Watcher.Action.DELETED, node);

        // expect node to be removed
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 0);
    }

    @Test(timeout = 10_000)
    public void testTerminateAgentOnContainerTerminated() throws IOException, InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = withContainerStatusTerminated(createPod(node));

        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
        .always();
        // don't remove pod on activate
        server.expect().withPath("/api/v1/namespaces/foo/pods/node-123").andReturn(200, node123).once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 1);

        // wait for the delete event to be processed
        waitForKubeClientRequests(6)
                .assertRequestCountAtLeast(watchPodsPath, 3);

        // verify listener got notified
        listener.waitForEvents()
                .expectEvent(Watcher.Action.MODIFIED, node);

        // expect node to be terminated
        verify(node, atLeastOnce()).terminate();
        // verify node is still registered (will be removed when pod deleted)
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 1);
    }

    @Test(timeout = 10_000)
    public void testTerminateAgentOnPodFailed() throws IOException, InterruptedException {
        System.out.println(server.getKubernetesMockServer().getPort());
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = createPod(node);
        node123.getStatus().setPhase("Failed");

        server.expect().withPath("/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true")
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
        .always();
        // don't remove pod on activate
        server.expect().withPath("/api/v1/namespaces/foo/pods/node-123").andReturn(200, node123).once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 1);

        // verify listener got notified
        listener.waitForEvents()
                .expectEvent(Watcher.Action.MODIFIED, node);

        // expect node to be terminated
        verify(node, atLeastOnce()).terminate();
        // verify node is still registered (will be removed when pod deleted)
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 1);
    }

    @Test(timeout = 10_000)
    public void testTerminateAgentOnImagePullBackoff() throws IOException, InterruptedException {
        KubernetesCloud cloud = addCloud("k8s", "foo");
        KubernetesSlave node = addNode(cloud, "node-123", "node");
        Pod node123 = withContainerImagePullBackoff(createPod(node));

        String watchPodsPath = "/api/v1/namespaces/foo/pods?allowWatchBookmarks=true&watch=true";
        server.expect().withPath(watchPodsPath)
                .andUpgradeToWebSocket()
                .open()
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "MODIFIED"))
                .waitFor(EVENT_WAIT_PERIOD_MS)
                .andEmit(new WatchEvent(node123, "BOOKMARK"))
                .done()
        .always();
        // don't remove pod on activate
        server.expect().withPath("/api/v1/namespaces/foo/pods/node-123").andReturn(200, node123).once();

        // activate reaper
        Reaper r = Reaper.getInstance();
        r.maybeActivate();

        // verify node is still registered
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 1);

        // wait for the delete event to be processed
        waitForKubeClientRequests(6)
                .assertRequestCountAtLeast(watchPodsPath, 3);

        // verify listener got notified
        listener.expectEvent(Watcher.Action.MODIFIED, node);

        // expect node to be terminated
        verify(node, atLeastOnce()).terminate();
        // verify node is still registered (will be removed when pod deleted)
        assertEquals("jenkins nodes", j.jenkins.getNodes().size(), 1);
    }

    private Pod withContainerImagePullBackoff(Pod pod) {
        ContainerStatus status = new ContainerStatus();
        ContainerState state = new ContainerState();
        ContainerStateWaiting waiting = new ContainerStateWaiting();
        waiting.setMessage("something Back-off pulling image something");
        state.setWaiting(waiting);
        status.setState(state);
        pod.getStatus().getContainerStatuses().add(status);
        return pod;
    }

    private Pod withContainerStatusTerminated(Pod pod) {
        ContainerStatus status = new ContainerStatus();
        ContainerState state = new ContainerState();
        ContainerStateTerminated terminated = new ContainerStateTerminated();
        terminated.setExitCode(123);
        terminated.setReason("because");
        state.setTerminated(terminated);
        status.setState(state);
        pod.getStatus().getContainerStatuses().add(status);
        return pod;
    }

    private Pod createPod(KubernetesSlave node) {
        Pod pod = new Pod();
        ObjectMeta meta = new ObjectMeta();
        meta.setNamespace(node.getNamespace());
        meta.setName(node.getPodName());
        pod.setMetadata(meta);
        pod.setSpec(new PodSpec());
        pod.setStatus(new PodStatus());
        return pod;
    }

    private KubernetesSlave addNode(KubernetesCloud cld, String podName, String nodeName) throws IOException {
        KubernetesSlave node = mock(KubernetesSlave.class);
        when(node.getNodeName()).thenReturn(nodeName);
        when(node.getNamespace()).thenReturn(cld.getNamespace());
        when(node.getPodName()).thenReturn(podName);
        when(node.getKubernetesCloud()).thenReturn(cld);
        when(node.getCloudName()).thenReturn(cld.name);
        when(node.getNumExecutors()).thenReturn(1);
        PodTemplate podTemplate = new PodTemplate();
        when(node.getTemplate()).thenReturn(podTemplate);
        ComputerLauncher launcher = mock(ComputerLauncher.class);
        when(node.getLauncher()).thenReturn(launcher);
        j.jenkins.addNode(node);
        return node;
    }

    private KubernetesCloud addCloud(String name, String namespace) {
        KubernetesCloud c = new KubernetesCloud(name);
        c.setServerUrl(server.getClient().getMasterUrl().toString());
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
    private CapturedRequests kubeClientRequests() throws InterruptedException {
        int count = server.getKubernetesMockServer().getRequestCount();
        List<RecordedRequest> requests = new LinkedList<>();
        while (count-- > 0) {
            RecordedRequest rr = server.getKubernetesMockServer().takeRequest(1, TimeUnit.SECONDS);
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
    private CapturedRequests waitForKubeClientRequests(int count) throws InterruptedException {
        List<RecordedRequest> requests = new LinkedList<>();
        while (count-- > 0) {
            requests.add(server.getKubernetesMockServer().takeRequest());
        }
        return new CapturedRequests(requests);
    }

    /**
     * Wait until the specified request is captured.
     * @param path number of requests to wait for
     * @return captured requests
     * @throws InterruptedException interrupted exception
     */
    private CapturedRequests waitForKubeClientRequests(String path) throws InterruptedException {
        List<RecordedRequest> requests = new LinkedList<>();
        while (true) {
            RecordedRequest rr = server.getKubernetesMockServer().takeRequest();
            requests.add(rr);
            if (rr.getPath().equals(path)) {
                return new CapturedRequests(requests);
            }
        }
    }

    private static class CapturedRequests {

        private final Map<String, Long> countByPath;

        CapturedRequests(List<RecordedRequest> requests) {
            this.countByPath = requests.stream().collect(Collectors.groupingBy(RecordedRequest::getPath, Collectors.counting()));
        }

        CapturedRequests assertRequestCount(String path, long count) {
            assertEquals(path + " count", count, (long) countByPath.getOrDefault(path, 0L));
            return this;
        }

        CapturedRequests assertRequestCountAtLeast(String path, long count) {
            assertThat(path + " count at least", countByPath.getOrDefault(path, 0L), Matchers.greaterThanOrEqualTo(count));
            return this;
        }
    }

    @Extension
    public static class CapturingReaperListener extends ExternalResource implements Reaper.Listener {

        private static final List<ReaperListenerWatchEvent> CAPTURED_EVENTS = new LinkedList<>();

        @Override
        public synchronized void onEvent(@NonNull Watcher.Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReaons) throws IOException, InterruptedException {
            CAPTURED_EVENTS.add(new ReaperListenerWatchEvent(action, node, pod));
            notifyAll();
        }

        /**
         * Test should use {@link #waitForEvents()}, not this method
         */
        private synchronized CapturingReaperListener waitForEventsOnJenkinsExtensionInstance() throws InterruptedException {
            while (CAPTURED_EVENTS.isEmpty()) {
                wait();
            }
            return this;
        }

        /**
         * Tests should use this method to wait for events to be processed by the Reaper cloud watcher.
         * @return jenkins extension instance
         * @throws InterruptedException if wait was interrupted
         */
        public CapturingReaperListener waitForEvents() throws InterruptedException {
            // find the instance that Jenkins created and wait on that one
            CapturingReaperListener l = Jenkins.get().getExtensionList(Reaper.Listener.class).get(CapturingReaperListener.class);
            if (l == null) {
                throw new RuntimeException("CapturingReaperListener not registered in Jenkins");
            }

            return l.waitForEventsOnJenkinsExtensionInstance();
        }

        /**
         * Verify that the watcher received an event of the given action and target node.
         * @param action action to match
         * @param node target node
         */
        public synchronized void expectEvent(Watcher.Action action, KubernetesSlave node) {
            boolean found = CAPTURED_EVENTS.stream().anyMatch(e -> e.action == action && e.node == node);
            assertTrue("expected event: " + action + ", " + node, found);
        }

        /**
         * Expect not event to have been received.
         */
        public synchronized void expectNoEvents() {
            assertEquals("no watcher events", 0, CAPTURED_EVENTS.size());
        }

        @Override
        protected void after() {
            CAPTURED_EVENTS.clear();
        }
    }

    private static class ReaperListenerWatchEvent {
        final Watcher.Action action;
        final KubernetesSlave node;
        final Pod pod;

        private ReaperListenerWatchEvent(Watcher.Action action, KubernetesSlave node, Pod pod) {
            this.action = action;
            this.node = node;
            this.pod = pod;
        }

        @Override
        public String toString() {
            return "[" + action + ", " + node + ", " + pod + "]";
        }
    }

    private static WatchEvent outdatedEvent() {
        return new WatchEventBuilder().withType(Watcher.Action.ERROR.name())
                .withStatusObject(
                        new StatusBuilder().withCode(HttpURLConnection.HTTP_GONE)
                                .withMessage(
                                        "410: The event in requested index is outdated and cleared (the requested history has been cleared [3/1]) [2]")
                                .build())
                .build();
    }

    private static WatchEvent errorEvent() {
        return new WatchEventBuilder().withType(Watcher.Action.ERROR.name())
                .withStatusObject(
                        new StatusBuilder().withCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                                .withMessage(
                                        "500: Internal error")
                                .build())
                .build();
    }
}
