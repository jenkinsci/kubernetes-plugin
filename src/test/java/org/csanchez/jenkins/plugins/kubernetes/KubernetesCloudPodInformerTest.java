package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.http.RecordedRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@EnableKubernetesMockClient(crud = true)
class KubernetesCloudPodInformerTest {

    private static final String JENKINS_URL = "http://jenkins.example/";

    private JenkinsRule j;
    private KubernetesMockServer server;
    private KubernetesClient client;
    private final ByteArrayOutputStream buildLog = new ByteArrayOutputStream();

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @AfterEach
    void afterEach() {
        KubernetesClientProvider.invalidateAll();
    }

    @Test
    void failedInformerIsStopped() {
        SharedIndexInformer<Pod> informer = mockInformer();
        doThrow(new KubernetesClientException("Forbidden", 403, null))
                .when(informer)
                .run();

        assertFalse(KubernetesCloud.runInformer(informer, "forbidden"));
        verify(informer).stop();
    }

    @Test
    void informerIsStoppedOnError() {
        SharedIndexInformer<Pod> informer = mockInformer();
        doThrow(new NoClassDefFoundError()).when(informer).run();

        assertThrows(NoClassDefFoundError.class, () -> KubernetesCloud.runInformer(informer, "ns"));
        verify(informer).stop();
    }

    @Test
    void stopFailureDoesNotEscape() {
        SharedIndexInformer<Pod> informer = mockInformer();
        doThrow(new KubernetesClientException("Forbidden", 403, null))
                .when(informer)
                .run();
        doThrow(new IllegalStateException()).when(informer).stop();

        assertFalse(KubernetesCloud.runInformer(informer, "forbidden"));
    }

    @Test
    void forbiddenNamespaceDoesNotFailRegistration() throws Exception {
        KubernetesCloud cloud = addCloud();
        server.expect()
                .get()
                .withPath("/api/v1/namespaces/forbidden/pods?labelSelector=jenkins%3Dslave%2C"
                        + "kubernetes.jenkins.io%2Fcontroller%3D"
                        + PodTemplateUtils.sanitizeLabel(JENKINS_URL)
                        + "&resourceVersion=0")
                .andReturn(403, "")
                .always();
        KubernetesSlave node = mockNode("forbidden");

        assertDoesNotThrow(() -> cloud.registerPodInformer(node));
        // Failure is not cached, so a later launch tries again
        assertDoesNotThrow(() -> cloud.registerPodInformer(node));
        assertEquals(2, countPodListRequests("forbidden"));
        assertThat(
                buildLog.toString(StandardCharsets.UTF_8), containsString("Cannot watch pods in namespace forbidden"));
    }

    @Test
    void startedInformerIsReused() throws Exception {
        KubernetesCloud cloud = addCloud();
        KubernetesSlave node = mockNode("allowed");

        try {
            cloud.registerPodInformer(node);
            cloud.registerPodInformer(node);
            assertEquals(1, countPodListRequests("allowed"));
        } finally {
            cloud.getInformers().values().forEach(SharedIndexInformer::stop);
        }
    }

    @Test
    void interruptedStartIsRethrown() {
        SharedIndexInformer<Pod> informer = mockInformer();
        doAnswer(inv -> {
                    Thread.currentThread().interrupt();
                    throw new KubernetesClientException("interrupted");
                })
                .when(informer)
                .run();

        try {
            assertThrows(KubernetesClientException.class, () -> KubernetesCloud.runInformer(informer, "ns"));
            verify(informer).stop();
        } finally {
            Thread.interrupted();
        }
    }

    @SuppressWarnings("unchecked")
    private static SharedIndexInformer<Pod> mockInformer() {
        return mock(SharedIndexInformer.class);
    }

    private KubernetesCloud addCloud() {
        KubernetesCloud cloud = new KubernetesCloud("k8s");
        cloud.setServerUrl(client.getMasterUrl().toString());
        cloud.setSkipTlsVerify(true);
        cloud.setJenkinsUrl(JENKINS_URL);
        j.jenkins.clouds.add(cloud);
        return cloud;
    }

    private KubernetesSlave mockNode(String namespace) {
        KubernetesSlave node = mock(KubernetesSlave.class);
        when(node.getNamespace()).thenReturn(namespace);
        when(node.getRunListener()).thenReturn(new StreamTaskListener(buildLog, StandardCharsets.UTF_8));
        return node;
    }

    /** Counts pod list (non-watch) requests in a namespace, draining the recorded requests. */
    private int countPodListRequests(String namespace) throws InterruptedException {
        int lists = 0;
        int count = server.getRequestCount();
        while (count-- > 0) {
            RecordedRequest r = server.takeRequest(1, TimeUnit.SECONDS);
            if (r != null
                    && r.getPath().startsWith("/api/v1/namespaces/" + namespace + "/pods?")
                    && !r.getPath().contains("watch=true")) {
                lists++;
            }
        }
        return lists;
    }
}
