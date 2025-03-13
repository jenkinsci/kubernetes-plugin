package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertTrue;

import hudson.model.Node;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration tests for {@link KubernetesLauncher}.
 */
public class KubernetesLauncherIntegrationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test that verifies the fix for the deadlock issue (JENKINS-73826).
     * This test simulates the scenario where multiple threads are trying to
     * access the KubernetesLauncher concurrently, which was causing deadlocks
     * in the original implementation.
     */
    @Test
    public void testNoDeadlockWithConcurrentAccess() throws Exception {
        // Create a Kubernetes cloud
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        j.jenkins.clouds.add(cloud);
        
        // Create a pod template with a valid ID
        String templateId = UUID.randomUUID().toString();
        PodTemplate podTemplate = new PodTemplate(templateId);
        podTemplate.setName("test-template");
        podTemplate.setLabel("test");
        cloud.addTemplate(podTemplate);
        
        // Create a Kubernetes slave
        KubernetesSlave slave = new KubernetesSlave(
                "test-slave",
                podTemplate,
                "test node",
                "kubernetes",
                "test",
                new KubernetesLauncher(),
                new OnceRetentionStrategy(10));
        
        j.jenkins.addNode(slave);
        
        // Get the computer for the slave
        SlaveComputer computer = slave.getComputer();
        
        // Create multiple threads to simulate concurrent access
        final int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicBoolean deadlockDetected = new AtomicBoolean(false);
        List<Future<?>> futures = new ArrayList<>();
        
        // Launch multiple threads that will try to access the launcher concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            Future<?> future = executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    KubernetesLauncher launcher = (KubernetesLauncher) slave.getLauncher();
                    
                    if (threadId % 2 == 0) {
                        // Half the threads will call isLaunchSupported
                        launcher.isLaunchSupported();
                    } else {
                        // The other half will try to access the node
                        Node node = j.jenkins.getNode(slave.getNodeName());
                        if (node != null) {
                            // Just access the node to simulate concurrent access
                            node.getNodeName();
                        }
                    }
                    
                } catch (Exception e) {
                    deadlockDetected.set(true);
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
            futures.add(future);
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete or timeout
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        
        // Shutdown the executor
        executor.shutdownNow();
        
        // Assert that all threads completed without deadlock
        assertTrue("Threads did not complete within timeout - possible deadlock", completed);
        assertTrue("Exception occurred during concurrent access", !deadlockDetected.get());
        
        // Check that all futures completed without exceptions
        for (Future<?> future : futures) {
            future.get(1, TimeUnit.SECONDS);
        }
    }
}
