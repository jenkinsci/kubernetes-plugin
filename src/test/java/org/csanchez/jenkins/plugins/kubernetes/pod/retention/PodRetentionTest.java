package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import static org.junit.Assert.*;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import java.util.function.Supplier;

public class PodRetentionTest {

    private KubernetesCloud cloud;
    private Pod pod;
    private Supplier<Pod> podS = () -> pod;

    @Before
    public void setUp() {
        this.cloud = new KubernetesCloud("kubernetes");
        this.pod = new Pod();
    }

    @Test
    public void testAlwaysPodRetention() {
        PodRetention subject = new Always();
        assertFalse(subject.shouldDeletePod(cloud, podS));
    }

    @Test
    public void testNeverPodRetention() {
        PodRetention subject = new Never();
        assertTrue(subject.shouldDeletePod(cloud, podS));
    }

    @Test
    public void testDefaultPodRetention() {
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
    public void testOnFailurePodRetention() {
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

    private PodStatus buildStatus(String phase) {
        return new PodStatusBuilder().withPhase(phase).build();
    }

}