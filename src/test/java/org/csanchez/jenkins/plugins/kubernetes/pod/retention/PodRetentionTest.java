package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import static org.junit.Assert.*;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.junit.Before;
import org.junit.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;

public class PodRetentionTest {

    private KubernetesCloud cloud;
    private Pod pod;

    @Before
    public void setUp() {
        this.cloud = new KubernetesCloud("kubernetes");
        this.pod = new Pod();
    }

    @Test
    public void testAlwaysPodRetention() {
        PodRetention subject = new Always();
        assertFalse(subject.shouldDeletePod(cloud, pod));
    }

    @Test
    public void testNeverPodRetention() {
        PodRetention subject = new Never();
        assertTrue(subject.shouldDeletePod(cloud, pod));
    }

    @Test
    public void testDefaultPodRetention() {
        PodRetention subject = new Default();
        cloud.setPodRetention(new Always());
        assertFalse(subject.shouldDeletePod(cloud, pod));
        cloud.setPodRetention(new Never());
        assertTrue(subject.shouldDeletePod(cloud, pod));
        cloud.setPodRetention(new Default());
        assertTrue(subject.shouldDeletePod(cloud, pod));
        cloud.setPodRetention(null);
        assertTrue(subject.shouldDeletePod(cloud, pod));
    }

    @Test
    public void testOnFailurePodRetention() {
        PodRetention subject = new OnFailure();
        pod.setStatus(buildStatus("Failed"));
        assertFalse(subject.shouldDeletePod(cloud, pod));
        pod.setStatus(buildStatus("Unknown"));
        assertFalse(subject.shouldDeletePod(cloud, pod));
        pod.setStatus(buildStatus("Running"));
        assertTrue(subject.shouldDeletePod(cloud, pod));
        pod.setStatus(buildStatus("Pending"));
        assertTrue(subject.shouldDeletePod(cloud, pod));
    }

    @Test
    public void testOnJobFailurePodRetention() {
        OnJobFailure subject = new OnJobFailure();

        // regular
        String runId = subject.getRunId("job/jobname/42/");
        assertEquals("42", runId);

        // nested
        runId = subject.getRunId("job/jobname1/job/jobname2/42/");
        assertEquals("42", runId);

        // folder name has numbers
        runId = subject.getRunId("job/22/42/");
        assertEquals("42", runId);
    }

    private PodStatus buildStatus(String phase) {
        return new PodStatusBuilder().withPhase(phase).build();
    }

}