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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import hudson.Util;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.rules.TestName;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.net.InetAddress;
import java.net.URL;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import static org.junit.Assert.*;
import org.junit.AssumptionViolatedException;
import org.jvnet.hudson.test.JenkinsRule;

public class KubernetesTestUtil {

    private static final Logger LOGGER = Logger.getLogger(KubernetesTestUtil.class.getName());

    private static final String DEFAULT_TESTING_NAMESPACE = "kubernetes-plugin-test";
    public static String testingNamespace;

    private static final String BRANCH_NAME = System.getenv("BRANCH_NAME");
    private static final String BUILD_NUMBER = System.getenv("BUILD_NUMBER");
    private static Map<String, String> DEFAULT_LABELS = ImmutableMap.of("BRANCH_NAME",
            BRANCH_NAME == null ? "undefined" : BRANCH_NAME, "BUILD_NUMBER",
            BUILD_NUMBER == null ? "undefined" : BUILD_NUMBER);

    public static final String SECRET_KEY = "password";
    public static final String CONTAINER_ENV_VAR_FROM_SECRET_VALUE = "container-pa55w0rd";
    public static final String POD_ENV_VAR_FROM_SECRET_VALUE = "pod-pa55w0rd";

    public static KubernetesCloud setupCloud(Object test, TestName name) throws KubernetesAuthException, IOException {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        // unique labels per test
        cloud.setPodLabels(PodLabel.fromMap(getLabels(cloud, test, name)));
        KubernetesClient client = cloud.connect();

        // Run in our own testing namespace

        // if there is a namespace specific for this branch (ie. kubernetes-plugin-test-master), use it
        String branch = System.getenv("BRANCH_NAME");
        if (StringUtils.isNotBlank(branch)) {
            String namespaceWithBranch = String.format("%s-%s", DEFAULT_TESTING_NAMESPACE, branch);
            LOGGER.log(FINE, "Trying to use namespace: {0}", testingNamespace);
            try {
                if (client.namespaces().withName(namespaceWithBranch).get() != null) {
                    testingNamespace = namespaceWithBranch;
                }
            } catch (KubernetesClientException e) {
                // nothing to do
            }
        }
        if (testingNamespace == null) {
            testingNamespace = DEFAULT_TESTING_NAMESPACE;
            if (client.namespaces().withName(testingNamespace).get() == null) {
                LOGGER.log(INFO, "Creating namespace: {0}", testingNamespace);
                client.namespaces().create(
                        new NamespaceBuilder().withNewMetadata().withName(testingNamespace).endMetadata().build());
            }
        }
        LOGGER.log(INFO, "Using namespace {0} for branch {1}", new String[] { testingNamespace, branch });
        cloud.setNamespace(testingNamespace);
        client = cloud.connect();

        return cloud;
    }

    public static void setupHost() throws Exception {
        // Agents running in Kubernetes (minikube) need to connect to this server, so localhost does not work
        URL url = new URL(JenkinsLocationConfiguration.get().getUrl());
        String hostAddress = System.getProperty("jenkins.host.address");
        if (org.apache.commons.lang3.StringUtils.isBlank(hostAddress)) {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        }
        System.err.println("Calling home to address: " + hostAddress);
        URL nonLocalhostUrl = new URL(url.getProtocol(), hostAddress, url.getPort(),
                url.getFile());
        // TODO better to set KUBERNETES_JENKINS_URL
        JenkinsLocationConfiguration.get().setUrl(nonLocalhostUrl.toString());

        Integer slaveAgentPort = Integer.getInteger("slaveAgentPort");
        if (slaveAgentPort != null) {
            Jenkins.get().setSlaveAgentPort(slaveAgentPort);
        }
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
     * Verifies that we are running in a mixed cluster with Windows nodes.
     * (The cluster is assumed to always have Linux nodes.)
     * This means that we can run tests involving Windows agent pods.
     * Note that running the <em>master</em> on Windows is untested.
     */
    public static void assumeWindows() {
        try (KubernetesClient client = new DefaultKubernetesClient(new ConfigBuilder(Config.autoConfigure(null)).build())) {
            for (Node n : client.nodes().list().getItems()) {
                String os = n.getMetadata().getLabels().get("kubernetes.io/os");
                LOGGER.info(() -> "Found node " + n.getMetadata().getName() + " running OS " + os);
                if ("windows".equals(os)) {
                    return;
                }
            }
        }
        throw new AssumptionViolatedException("Cluster seems to contain no Windows nodes");
    }

    public static Map<String, String> getLabels(Object o, TestName name) {
        return getLabels(null, o, name);
    }

    /**
     * Labels to add to the pods so we can match them to a specific build run, test class and method
     */
    public static Map<String, String> getLabels(KubernetesCloud cloud, Object o, TestName name) {
        HashMap<String, String> l = Maps.newHashMap(DEFAULT_LABELS);
        if (cloud != null) {
            l.putAll(cloud.getPodLabelsMap());
        }
        l.put("class", o.getClass().getSimpleName());
        l.put("test", name.getMethodName());
        return l;
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

    public static void createSecret(KubernetesClient client, String namespace) {
        Secret secret = new SecretBuilder()
                .withStringData(ImmutableMap.of(SECRET_KEY, CONTAINER_ENV_VAR_FROM_SECRET_VALUE)).withNewMetadata()
                .withName("container-secret").endMetadata().build();
        secret = client.secrets().inNamespace(namespace).createOrReplace(secret);

        LOGGER.log(Level.INFO, "Created container secret: " + Serialization.asYaml(secret));
        secret = new SecretBuilder().withStringData(ImmutableMap.of(SECRET_KEY, POD_ENV_VAR_FROM_SECRET_VALUE))
                .withNewMetadata().withName("pod-secret").endMetadata().build();
        secret = client.secrets().inNamespace(namespace).createOrReplace(secret);
        LOGGER.log(Level.INFO, "Created pod secret: " + Serialization.asYaml(secret));

        secret = new SecretBuilder().withStringData(ImmutableMap.of(SECRET_KEY, ""))
                .withNewMetadata().withName("empty-secret").endMetadata().build();
        secret = client.secrets().inNamespace(namespace).createOrReplace(secret);
        LOGGER.log(Level.INFO, "Created pod secret: " + Serialization.asYaml(secret));
    }

    public static String generateProjectName(String name) {
        return name.replaceAll("([A-Z])", " $1");
    }

    public static WorkflowRun createPipelineJobThenScheduleRun(JenkinsRule r, Class cls, String methodName) throws InterruptedException, ExecutionException, IOException {
        return createPipelineJobThenScheduleRun(r, cls, methodName, null);
    }

    public static WorkflowRun createPipelineJobThenScheduleRun(JenkinsRule r, Class cls, String methodName, Map<String, String> env) throws IOException, ExecutionException, InterruptedException {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, generateProjectName(methodName));
        p.setDefinition(new CpsFlowDefinition(loadPipelineDefinition(cls, methodName, env), true));
        return p.scheduleBuild2(0).waitForStart();
    }

    public static String loadPipelineDefinition(Class cls, String name, Map<String, String> providedEnv) {
        Map<String, String> env = providedEnv == null ? new HashMap<>() : new HashMap<>(providedEnv);
        // TODO get rid of $NAME substitution once all remaining tests stop using it
        env.put("NAME", name);
        return Util.replaceMacro(loadPipelineScript(cls, name + ".groovy"), env);
    }

    public static String loadPipelineScript(Class<?> clazz, String name) {
        try {
            return new String(IOUtils.toByteArray(clazz.getResourceAsStream(name)));
        } catch (Throwable t) {
            throw new RuntimeException("Could not read resource:[" + name + "].");
        }
    }

    public static void assertRegex(String name, String regex) {
        assertNotNull(name);
        assertTrue(String.format("Name does not match regex [%s]: '%s'", regex, name), name.matches(regex));
    }

}
