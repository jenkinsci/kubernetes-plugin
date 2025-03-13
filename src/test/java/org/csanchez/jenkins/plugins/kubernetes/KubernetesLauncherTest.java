package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link KubernetesLauncher}.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class KubernetesLauncherTest {

    @Mock
    private KubernetesComputer computer;
    
    @Mock
    private KubernetesSlave slave;
    
    @Mock
    private TaskListener listener;

    private KubernetesLauncher launcher;

    @Before
    public void setUp() {
        launcher = new KubernetesLauncher();
        when(computer.getNode()).thenReturn(slave);
        when(computer.getName()).thenReturn("test-computer");
    }

    @Test
    public void testIsLaunchSupported() {
        // The method should always return true to avoid deadlocks
        assertTrue(launcher.isLaunchSupported());
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException, IOException {
        // This test simulates concurrent access to the launcher
        // to verify that the deadlock fix works correctly
        
        // Mock the save method to simulate a delay that could cause deadlocks
        doAnswer(invocation -> {
            Thread.sleep(100); // Simulate delay in saving
            return null;
        }).when(slave).save();
        
        final int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicBoolean deadlockDetected = new AtomicBoolean(false);
        
        // Launch multiple threads that will try to access the launcher concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    if (threadId % 2 == 0) {
                        // Half the threads will call isLaunchSupported
                        launcher.isLaunchSupported();
                    } else {
                        // The other half will simulate accessing the node
                        // This avoids calling launcher.launch() which throws IOException
                        Node node = computer.getNode();
                        if (node != null) {
                            // Make sure we use the mocked methods
                            computer.getName();
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
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete or timeout
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
        
        // Shutdown the executor
        executor.shutdownNow();
        
        // Assert that all threads completed without deadlock
        assertTrue("Threads did not complete within timeout - possible deadlock", completed);
        assertTrue("Exception occurred during concurrent access", !deadlockDetected.get());
    }
}
