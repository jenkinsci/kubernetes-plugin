package org.csanchez.jenkins.plugins.kubernetes;

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Deprecated, use KeyValueEnvVar
 */
@Deprecated
public class PodEnvVar extends KeyValueEnvVar {

    @DataBoundConstructor
    public PodEnvVar(String key, String value) {
        super(key, value);
    }

    @Override
    public String toString() {
        return "PodEnvVar [getValue()=" + getValue() + ", getKey()=" + getKey() + "]";
    }

    public static class EnvironmentVariableNames {

        static final String JENKINS_URL = "JENKINS_URL";
        static final String JENKINS_LOCATION_URL = "JENKINS_LOCATION_URL";
        static final String JENKINS_TUNNEL = "JENKINS_TUNNEL";
        static final String JENKINS_SECRET = "JENKINS_SECRET";
        static final String JENKINS_NAME = "JENKINS_NAME";
        static final String JENKINS_JNLP_URL = "JENKINS_JNLP_URL";
        static final String HOME = "HOME";

        private EnvironmentVariableNames() {}

    }
}