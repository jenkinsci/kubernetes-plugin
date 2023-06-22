package org.csanchez.jenkins.plugins.kubernetes;

import java.util.Locale;

import hudson.model.Label;

public class MetricNames {
    private static final String PREFIX = "kubernetes.cloud";

    public static final String CREATION_FAILED = PREFIX + ".pods.creation.failed";
    public static final String PODS_CREATED = PREFIX + ".pods.created";
    public static final String LAUNCH_FAILED = PREFIX + ".pods.launch.failed";
    public static final String PODS_TERMINATED = PREFIX + ".pods.terminated";
    public static final String REACHED_POD_CAP = PREFIX + ".provision.reached.pod.cap";
    public static final String REACHED_GLOBAL_CAP = PREFIX + ".provision.reached.global.cap";
    public static final String FAILED_TIMEOUT = PREFIX + ".pods.launch.failed.timeout";
    public static final String PROVISION_NODES = PREFIX + ".provision.nodes";
    public static final String PROVISION_FAILED = PREFIX + ".provision.failed";
    public static final String PODS_LAUNCHED = PREFIX + ".pods.launched";

    public static String metricNameForPodStatus(String status) {
        String formattedStatus = status == null ? "null" : status.toLowerCase(Locale.getDefault());
        return PREFIX + ".pods.launch.status." + formattedStatus;
    }

    public static String metricNameForLabel(Label label) {
        String labelText = (label == null) ? "nolabel" : label.getDisplayName();
        return String.format("%s.%s.provision.request", PREFIX, labelText);
    }
}
