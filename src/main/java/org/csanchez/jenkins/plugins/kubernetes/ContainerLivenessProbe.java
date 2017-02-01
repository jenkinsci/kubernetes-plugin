package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serializable;

/**
 * Created by fabricio.leotti on 30/01/17.
 */
public class ContainerLivenessProbe extends AbstractDescribableImpl<ContainerLivenessProbe> implements Serializable {
    private String execArgs;
    private Integer timeoutSeconds;
    private Integer initialDelaySeconds;
    private Integer failureThreshold;
    private Integer periodSeconds;
    private Integer successThreshold;

    @DataBoundConstructor
    public ContainerLivenessProbe(String execArgs, Integer timeoutSeconds, Integer initialDelaySeconds, Integer failureThreshold, Integer periodSeconds, Integer successThreshold) {
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

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getInitialDelaySeconds() {
        return initialDelaySeconds;
    }

    public void setInitialDelaySeconds(Integer initialDelaySeconds) {
        this.initialDelaySeconds = initialDelaySeconds;
    }

    public Integer getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(Integer failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public Integer getPeriodSeconds() {
        return periodSeconds;
    }

    public void setPeriodSeconds(Integer periodSeconds) {
        this.periodSeconds = periodSeconds;
    }

    public Integer getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(Integer successThreshold) {
        this.successThreshold = successThreshold;
    }

    @Extension
    @Symbol("containerLivenessProbe")
    public static class DescriptorImpl extends Descriptor<ContainerLivenessProbe> {
        @Override
        public String getDisplayName() {
            return "Container Liveness Probe";
        }

        public FormValidation doCheckExecArgs(@QueryParameter String execArgs) {
            if(execArgs == null || execArgs.isEmpty()) {
                return FormValidation.error("Liveness Probe > Exec Action should not be empty");
            } else {
                return FormValidation.ok();
            }
        }
    }
}
