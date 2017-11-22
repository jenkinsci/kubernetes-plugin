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

import static java.util.logging.Level.*;
import static org.junit.Assume.*;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;

public class KubernetesTestUtil {

    private static final Logger LOGGER = Logger.getLogger(KubernetesTestUtil.class.getName());

    public static final String TESTING_NAMESPACE = "kubernetes-plugin-test";

    public static KubernetesCloud setupCloud() throws UnrecoverableKeyException, CertificateEncodingException,
            NoSuchAlgorithmException, KeyStoreException, IOException {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        KubernetesClient client = cloud.connect();

        // Run in our own testing namespace
        if (client.namespaces().withName(TESTING_NAMESPACE).get() == null) {
            client.namespaces()
                    .create(new NamespaceBuilder().withNewMetadata().withName(TESTING_NAMESPACE).endMetadata().build());
        }
        cloud.setNamespace(TESTING_NAMESPACE);
        client = cloud.connect();

        return cloud;
    }

    public static void assumeKubernetes() throws Exception {
        try (DefaultKubernetesClient client = new DefaultKubernetesClient(
                new ConfigBuilder(Config.autoConfigure(null)).build())) {
            client.pods().list();
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    /**
     * Delete pods with matching labels
     * 
     * @param client
     * @param labels
     * @param wait
     *            wait some time for pods to finish
     * @return whether any pod was deleted
     * @throws Exception
     */
    public static boolean deletePods(KubernetesClient client, Map<String, String> labels, boolean wait)
            throws Exception {

        if (client != null) {

            // wait for 90 seconds for all pods to be terminated
            if (wait) {
                LOGGER.log(INFO, "Waiting for pods to terminate");
                ForkJoinPool forkJoinPool = new ForkJoinPool(1);
                try {
                    forkJoinPool.submit(() -> IntStream.range(1, 1_000_000).anyMatch(i -> {
                        try {
                            FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods = client.pods()
                                    .withLabels(labels);
                            LOGGER.log(INFO, "Still waiting for pods to terminate: {0}", print(pods));
                            boolean allTerminated = pods.list().getItems().isEmpty();
                            if (allTerminated) {
                                LOGGER.log(INFO, "All pods are terminated: {0}", print(pods));
                            } else {
                                LOGGER.log(INFO, "Still waiting for pods to terminate: {0}", print(pods));
                                Thread.sleep(5000);
                            }
                            return allTerminated;
                        } catch (InterruptedException e) {
                            LOGGER.log(INFO, "Waiting for pods to terminate - interrupted");
                            return true;
                        }
                    })).get(90, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    LOGGER.log(INFO, "Waiting for pods to terminate - timed out");
                    // job not done in interval
                }
            }

            FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods = client.pods()
                    .withLabels(labels);
            if (!pods.list().getItems().isEmpty()) {
                LOGGER.log(WARNING, "Deleting leftover pods: {0}", print(pods));
                if (Boolean.TRUE.equals(pods.delete())) {
                    return true;
                }

            }
        }
        return false;
    }

    private static List<String> print(FilterWatchListDeletable<Pod, PodList, Boolean, Watch, Watcher<Pod>> pods) {
        return pods.list().getItems().stream()
                .map(pod -> String.format("%s (%s)", pod.getMetadata().getName(), pod.getStatus().getPhase()))
                .collect(Collectors.toList());
    }
}