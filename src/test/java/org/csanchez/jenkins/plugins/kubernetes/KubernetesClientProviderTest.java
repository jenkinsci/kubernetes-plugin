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
package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Always;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class KubernetesClientProviderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testGetValidity() {
        KubernetesCloud cloud = new KubernetesCloud("foo");
        // changes to these properties should trigger different validity value
        checkValidityChanges(
                cloud,
                c -> c.setServerUrl("https://server:443"),
                c -> c.setNamespace("blue"),
                c -> c.setServerCertificate("cert"),
                c -> c.setCredentialsId("secret"),
                c -> c.setSkipTlsVerify(true),
                c -> c.setConnectTimeout(46),
                c -> c.setReadTimeout(43),
                c -> c.setMaxRequestsPerHost(47),
                c -> c.setUseJenkinsProxy(true));

        // changes to these properties should not trigger different validity value
        checkValidityDoesNotChange(
                cloud,
                c -> c.setPodLabels(PodLabel.listOf("foo", "bar")),
                c -> c.setJenkinsUrl("https://localhost:8081"),
                c -> c.setJenkinsTunnel("https://jenkins.cluster.svc"),
                c -> c.setPodRetention(new Always()),
                c -> c.setWebSocket(true),
                c -> c.setRetentionTimeout(52),
                c -> c.setDirectConnection(true));

        // verify stability
        assertEquals(KubernetesClientProvider.getValidity(cloud), KubernetesClientProvider.getValidity(cloud));
    }

    private void checkValidityChanges(KubernetesCloud cloud, Consumer<KubernetesCloud>... mutations) {
        checkValidity(cloud, Assert::assertNotEquals, mutations);
    }

    private void checkValidityDoesNotChange(KubernetesCloud cloud, Consumer<KubernetesCloud>... mutations) {
        checkValidity(cloud, Assert::assertEquals, mutations);
    }

    private void checkValidity(
            KubernetesCloud cloud, ValidityAssertion validityAssertion, Consumer<KubernetesCloud>... mutations) {
        int v = KubernetesClientProvider.getValidity(cloud);
        int count = 1;
        for (Consumer<KubernetesCloud> mut : mutations) {
            mut.accept(cloud);
            int after = KubernetesClientProvider.getValidity(cloud);
            validityAssertion.doAssert("change #" + count++ + " of " + mutations.length, v, after);
            v = after;
        }
    }

    interface ValidityAssertion {
        void doAssert(String message, int before, int after);
    }

    /**
     * Reproducer for JENKINS-76095 / BEE-62473: Kubernetes client leaks Thread Pool Executors
     *
     * This test verifies that when cached Kubernetes clients expire, their internal thread pools
     * are properly closed and cleaned up.
     */
    @Test
    public void testExpiredClientThreadsAreCleaned() throws Exception {
        // Save original cache expiration value
        String originalExpiration = System.getProperty(
                "org.csanchez.jenkins.plugins.kubernetes.clients.cacheExpiration");

        try {
            // Set aggressive cache expiration (1 second) to speed up test
            System.setProperty("org.csanchez.jenkins.plugins.kubernetes.clients.cacheExpiration", "1");

            // Clear any existing cached clients
            KubernetesClientProvider.invalidateAll();

            // Get baseline thread count for OkHttp threads (used by fabric8 kubernetes client)
            int initialThreadCount = countKubernetesClientThreads();

            // Create a Kubernetes cloud and get a client (this will cache it)
            KubernetesCloud cloud = new KubernetesCloud("test-cloud");
            cloud.setServerUrl("https://fake-kubernetes-server:443");
            cloud.setSkipTlsVerify(true);
            cloud.setNamespace("default");

            r.jenkins.clouds.add(cloud);

            // This creates and caches a client
            KubernetesClient client = KubernetesClientProvider.createClient(cloud);

            // Verify client was created and has threads
            int threadsAfterCreation = countKubernetesClientThreads();
            assertTrue(
                    "Client should have created threads (before: " + initialThreadCount + ", after: "
                            + threadsAfterCreation + ")",
                    threadsAfterCreation > initialThreadCount);

            // Wait for cache expiration (1 second) plus some buffer for cleanup
            TimeUnit.SECONDS.sleep(3);

            // Trigger cache maintenance to process expired entries
            // In real scenario this happens automatically, but we force it for the test
            System.gc(); // Hint to run GC which may help trigger cache cleanup
            TimeUnit.MILLISECONDS.sleep(500);

            // Count threads after expiration
            int threadsAfterExpiration = countKubernetesClientThreads();

            // The test expectation: threads should be cleaned up after client expiration
            // Note: This test will FAIL on the current code (before fix is applied)
            // and PASS after the fix is implemented
            assertTrue(
                    "Kubernetes client threads should be cleaned up after cache expiration. "
                            + "Before: " + initialThreadCount + ", after creation: " + threadsAfterCreation
                            + ", after expiration: " + threadsAfterExpiration,
                    threadsAfterExpiration <= initialThreadCount + 2); // Allow small margin for timing

        } finally {
            // Restore original cache expiration value
            if (originalExpiration != null) {
                System.setProperty("org.csanchez.jenkins.plugins.kubernetes.clients.cacheExpiration",
                        originalExpiration);
            } else {
                System.clearProperty("org.csanchez.jenkins.plugins.kubernetes.clients.cacheExpiration");
            }
            KubernetesClientProvider.invalidateAll();
        }
    }

    /**
     * Count threads created by Kubernetes client (OkHttp, fabric8, etc.)
     * These threads typically have names containing "OkHttp", "kubernetes-client", or similar patterns
     */
    private int countKubernetesClientThreads() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);

        return (int) Arrays.stream(threadInfos)
                .map(ThreadInfo::getThreadName)
                .filter(name -> name != null && (
                        name.contains("OkHttp")
                        || name.contains("kubernetes")
                        || name.contains("okhttp")))
                .count();
    }
}
