package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import java.util.function.Supplier;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PodRetentionTest {

    private KubernetesCloud cloud;
    private Pod pod;
    private Supplier<Pod> podS = () -> pod;

    @BeforeEach
    void beforeEach() {
        this.cloud = new KubernetesCloud("kubernetes");
        this.pod = new Pod();
    }

    @Test
    void testAlwaysPodRetention() {
        PodRetention subject = new Always();
        assertFalse(subject.shouldDeletePod(cloud, podS));
    }

    @Test
    void testNeverPodRetention() {
        PodRetention subject = new Never();
        assertTrue(subject.shouldDeletePod(cloud, podS));
    }

    @Test
    void testDefaultPodRetention() {
        PodRetention subject = new Default();
        cloud.setPodRetention(new Always());
        assertFalse(subject.shouldDeletePod(cloud, podS));
        cloud.setPodRetention(new Never());
        assertTrue(subject.shouldDeletePod(cloud, podS));
        cloud.setPodRetention(new Default());
        assertTrue(subject.shouldDeletePod(cloud, podS));
        cloud.setPodRetention(null);
        assertTrue(subject.shouldDeletePod(cloud, podS));
    }

    @Test
    void testOnFailurePodRetention() {
        PodRetention subject = new OnFailure();
        pod.setStatus(buildStatus("Failed"));
        assertFalse(subject.shouldDeletePod(cloud, podS));
        pod.setStatus(buildStatus("Unknown"));
        assertFalse(subject.shouldDeletePod(cloud, podS));
        pod.setStatus(buildStatus("Running"));
        assertTrue(subject.shouldDeletePod(cloud, podS));
        pod.setStatus(buildStatus("Pending"));
        assertTrue(subject.shouldDeletePod(cloud, podS));
    }

    @Test
    void testOnEvictedPodRetention() {
        PodRetention subject = new Evicted();
        pod.setStatus(buildStatus("Failed", "Evicted"));
        assertFalse(subject.shouldDeletePod(cloud, podS));
        pod.setStatus(buildStatus("Failed", "OOMKilled"));
        assertTrue(subject.shouldDeletePod(cloud, podS));
    }

    private PodStatus buildStatus(String phase) {
        return new PodStatusBuilder().withPhase(phase).build();
    }

    private PodStatus buildStatus(String phase, String reason) {
        return new PodStatusBuilder().withPhase(phase).withReason(reason).build();
    }
}
