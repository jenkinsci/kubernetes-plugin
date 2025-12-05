package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MetricNamesTest {

    @Test
    void metricNameForPodStatusAddsNullWhenStatusIsNull() {
        String expected = "kubernetes.cloud.pods.launch.status.null";
        String actual = MetricNames.metricNameForPodStatus(null);

        assertEquals(expected, actual);
    }

    @Test
    void metricNameForPodStatusAddsStatusValueIfNotNull() {
        String expected = "kubernetes.cloud.pods.launch.status.running";
        String actual = MetricNames.metricNameForPodStatus("RUNNING");

        assertEquals(expected, actual);
    }

    @Test
    void metricNameForPodStatusChangeStatusToLowercase() {
        String expected = "kubernetes.cloud.pods.launch.status.failed";
        String actual = MetricNames.metricNameForPodStatus("FaIlEd");

        assertEquals(expected, actual);
    }
}
