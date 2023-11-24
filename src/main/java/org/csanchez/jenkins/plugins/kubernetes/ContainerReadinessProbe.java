package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

/**
 * Created by geanpalacios on 15/02/23.
 */
public class ContainerReadinessProbe extends AbstractDescribableImpl<ContainerReadinessProbe> implements Serializable {
        private String execArgs;
        private int timeoutSeconds;
        private int initialDelaySeconds;
        private int failureThreshold;
        private int periodSeconds;
        private int successThreshold;

    @DataBoundConstructor
    public ContainerReadinessProbe(String execArgs, int timeoutSeconds, int initialDelaySeconds, int failureThreshold, int periodSeconds, int successThreshold) {
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
        this.execArgs = execArgs;
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
        return "ContainerReadinessProbe{" +
                "execArgs='" + execArgs + '\'' +
                ", timeoutSeconds=" + timeoutSeconds +
                ", initialDelaySeconds=" + initialDelaySeconds +
                ", failureThreshold=" + failureThreshold +
                ", periodSeconds=" + periodSeconds +
                ", successThreshold=" + successThreshold +
                '}';
    }

    @Extension
    @Symbol("containerReadinessProbe")
    public static class DescriptorImpl extends Descriptor<ContainerReadinessProbe> {
        @Override
        public String getDisplayName() {
            return "Container Readiness Probe";
        }
    }
}
