package org.csanchez.jenkins.plugins.kubernetes;

import antlr.ANTLRException;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
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

    @Test
    public void metricNameForLabelAddsNoLabelIfLabelIsNull() {
        String expected = "kubernetes.cloud.nolabel.provision.request";
        String actual = MetricNames.metricNameForLabel(null);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void metricNameForLabelAddsLabelValue() {
        String expected = "kubernetes.cloud.java.provision.request";
        String actual = MetricNames.metricNameForLabel(new LabelAtom("java"));

        Assert.assertEquals(expected, actual);
    }
}