package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;

/**
 * Created by fabricio.leotti on 26/04/17.
 */
public class ContainerLivenessProbe extends AbstractDescribableImpl<ContainerLivenessProbe> implements Serializable {
        private String execArgs = DescriptorImpl.DEFAULT_EXEC_ARGS;
        private int timeoutSeconds = DescriptorImpl.DEFAULT_TIMEOUT_SECONDS;
        private int initialDelaySeconds = DescriptorImpl.DEFAULT_INITIAL_DELAY_SECONDS;
        private int failureThreshold = DescriptorImpl.DEFAULT_FAILURE_THRESHOLD;
        private int periodSeconds = DescriptorImpl.DEFAULT_PERIOD_SECONDS;
        private int successThreshold = DescriptorImpl.DEFAULT_SUCCESS_THRESHOLD;

    @DataBoundConstructor
    public ContainerLivenessProbe(String execArgs, int timeoutSeconds, int initialDelaySeconds, int failureThreshold, int periodSeconds, int successThreshold) {
        this.execArgs = execArgs;
        this.timeoutSeconds = timeoutSeconds;
        this.initialDelaySeconds = initialDelaySeconds;
        this.failureThreshold = failureThreshold;
        this.periodSeconds = periodSeconds;
        this.successThreshold = successThreshold;
    }

    public String getExecArgs() {
        return execArgs;
    }

    public void setExecArgs(String execArgs) {
        this.execArgs = Util.fixEmpty(execArgs);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getInitialDelaySeconds() {
        return initialDelaySeconds;
    }

    public void setInitialDelaySeconds(int initialDelaySeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public int getPeriodSeconds() {
        return periodSeconds;
    }

    public void setPeriodSeconds(int periodSeconds) {
        this.periodSeconds = periodSeconds;
    }

    public int getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(int successThreshold) {
        this.successThreshold = successThreshold;
    }

    @Override
    public String toString() {
        return "ContainerLivenessProbe{" +
                "execArgs='" + execArgs + '\'' +
                ", timeoutSeconds=" + timeoutSeconds +
                ", initialDelaySeconds=" + initialDelaySeconds +
                ", failureThreshold=" + failureThreshold +
                ", periodSeconds=" + periodSeconds +
                ", successThreshold=" + successThreshold +
                '}';
    }

    @Extension
    @Symbol("containerLivenessProbe")
    public static class DescriptorImpl extends Descriptor<ContainerLivenessProbe> {
        @Override
        public String getDisplayName() {
            return "Container Liveness Probe";
        }

        public static final String DEFAULT_EXEC_ARGS = null;
        public static final int    DEFAULT_TIMEOUT_SECONDS = 1;
        public static final int    DEFAULT_INITIAL_DELAY_SECONDS = 0;
        public static final int    DEFAULT_PERIOD_SECONDS = 10;
        public static final int    DEFAULT_FAILURE_THRESHOLD = 3;
        public static final int    DEFAULT_SUCCESS_THRESHOLD = 1;

        public FormValidation doCheckExecArgs(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Liveness probe command is mandatory");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckTimeoutSeconds(@QueryParameter int value) {
            if (value < 1) {
                return FormValidation.error("Minimum Timeout Seconds value is 1");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckInitialDelaySeconds(@QueryParameter int value) {
            if (value < 0) {
                return FormValidation.error("Minimum Initial Delay Seconds value is 0");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckPeriodSeconds(@QueryParameter int value) {
            if (value < 1) {
                return FormValidation.error("Minimum Period Seconds value is 1");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckFailureThreshold(@QueryParameter int value) {
            if (value < 1) {
                return FormValidation.error("Minimum Failure Threshold value is 1");
            } else {
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckSuccessThreshold(@QueryParameter int value) {
            if (value < 1) {
                return FormValidation.error("Minimum Success Threshold value is 1");
            } else {
                return FormValidation.ok();
            }
        }
    }

    public static ContainerLivenessProbe getDefault() {
        return new ContainerLivenessProbe(DescriptorImpl.DEFAULT_EXEC_ARGS,
                DescriptorImpl.DEFAULT_TIMEOUT_SECONDS,
                DescriptorImpl.DEFAULT_INITIAL_DELAY_SECONDS,
                DescriptorImpl.DEFAULT_FAILURE_THRESHOLD,
                DescriptorImpl.DEFAULT_PERIOD_SECONDS,
                DescriptorImpl.DEFAULT_SUCCESS_THRESHOLD);
    }
}
