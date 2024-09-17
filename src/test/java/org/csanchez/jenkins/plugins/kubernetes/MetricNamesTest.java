package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Assert;
import org.junit.Test;

public class MetricNamesTest {

    @Test
    public void metricNameForPodStatusAddsNullWhenStatusIsNull() {
        String expected = "kubernetes.cloud.pods.launch.status.null";
        String actual = MetricNames.metricNameForPodStatus(null);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void metricNameForPodStatusAddsStatusValueIfNotNull() {
        String expected = "kubernetes.cloud.pods.launch.status.running";
        String actual = MetricNames.metricNameForPodStatus("RUNNING");

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void metricNameForPodStatusChangeStatusToLowercase() {
        String expected = "kubernetes.cloud.pods.launch.status.failed";
        String actual = MetricNames.metricNameForPodStatus("FaIlEd");

        Assert.assertEquals(expected, actual);
    }
}
