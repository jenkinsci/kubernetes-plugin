package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.http.RecordedRequest;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.csanchez.jenkins.plugins.kubernetes.watch.PodStatusEventHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@EnableKubernetesMockClient(crud = true)
class KubernetesCloudPodInformerTest {

    private static final String JENKINS_URL = "http://jenkins.example/";

    private JenkinsRule j;
    private KubernetesMockServer server;
    private KubernetesClient client;
    private KubernetesCloud cloud;
    private final ByteArrayOutputStream buildLog = new ByteArrayOutputStream();

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        cloud = new KubernetesCloud("k8s");
        cloud.setServerUrl(client.getMasterUrl().toString());
        cloud.setSkipTlsVerify(true);
        cloud.setJenkinsUrl(JENKINS_URL);
        j.jenkins.clouds.add(cloud);
    }

    @AfterEach
    void afterEach() {
        cloud.getInformers().values().forEach(SharedIndexInformer::stop);
        KubernetesClientProvider.invalidateAll();
    }

    @Test
    void failedInformerIsReleased() throws Exception {
        forbidPodList("forbidden", "labelSelector=jenkins%3Dslave&resourceVersion=0");
        // Without the stop, fabric8's shared scheduler keeps the informer's resync task, and so the informer, reachable
        MemoryAssert.assertGC(
                runInformer(informer -> assertFalse(KubernetesCloud.runInformer(informer, "forbidden"))), false);
    }

    @Test
    void interruptedStartIsRethrown() throws Exception {
        forbidPodList("forbidden", "labelSelector=jenkins%3Dslave&resourceVersion=0");
        WeakReference<SharedIndexInformer<Pod>> ref;
        Thread.currentThread().interrupt();
        try {
            ref = runInformer(informer -> assertThrows(
                    KubernetesClientException.class, () -> KubernetesCloud.runInformer(informer, "forbidden")));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        MemoryAssert.assertGC(ref, false);
    }

    @Test
    void forbiddenNamespaceDoesNotFailRegistration() throws Exception {
        forbidPodList(
                "forbidden",
                "labelSelector=jenkins%3Dslave%2Ckubernetes.jenkins.io%2Fcontroller%3D"
                        + PodTemplateUtils.sanitizeLabel(JENKINS_URL)
                        + "&resourceVersion=0");
        KubernetesSlave node = newNode("forbidden");

        assertDoesNotThrow(() -> cloud.registerPodInformer(node));
        // Failure is not cached, so a later launch tries again
        assertDoesNotThrow(() -> cloud.registerPodInformer(node));
        assertEquals(2, countPodListRequests("forbidden"));
        assertThat(
                buildLog.toString(StandardCharsets.UTF_8), containsString("Cannot watch pods in namespace forbidden"));
    }

    @Test
    void startedInformerIsReused() throws Exception {
        KubernetesSlave node = newNode("allowed");

        cloud.registerPodInformer(node);
        cloud.registerPodInformer(node);
        assertEquals(1, countPodListRequests("allowed"));
    }

    private void forbidPodList(String namespace, String query) {
        server.expect()
                .get()
                .withPath("/api/v1/namespaces/" + namespace + "/pods?" + query)
                .andReturn(403, "")
                .always();
    }

    /** Builds an informer like {@link KubernetesCloud#registerPodInformer} does, runs the check on it, and lets it go. */
    private WeakReference<SharedIndexInformer<Pod>> runInformer(Consumer<SharedIndexInformer<Pod>> check)
            throws Exception {
        SharedIndexInformer<Pod> informer = cloud.connect()
                .pods()
                .inNamespace("forbidden")
                .withLabels(Map.of("jenkins", "slave"))
                .runnableInformer(TimeUnit.SECONDS.toMillis(30));
        informer.addEventHandler(new PodStatusEventHandler());
        check.accept(informer);
        return new WeakReference<>(informer);
    }

    private KubernetesSlave newNode(String namespace) throws Exception {
        PodTemplate template = new PodTemplate();
        template.setName("informer-test");
        template.setListener(new StreamTaskListener(buildLog, StandardCharsets.UTF_8));
        KubernetesSlave node =
                KubernetesSlave.builder().podTemplate(template).cloud(cloud).build();
        node.setNamespace(namespace);
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
