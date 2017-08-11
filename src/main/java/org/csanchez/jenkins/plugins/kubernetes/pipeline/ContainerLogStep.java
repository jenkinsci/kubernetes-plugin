package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Set;

public class ContainerLogStep extends Step implements Serializable {
    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;
    private boolean returnLog = false;
    private int tailingLines = 0;
    private int sinceSeconds = 0;
    private int limitBytes = 0;

    @DataBoundConstructor
    public ContainerLogStep(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setReturnLog(boolean returnLog) {
        this.returnLog = returnLog;
    }

    public boolean isReturnLog() {
        return returnLog;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ContainerLogStepExecution(this, context);
    }

    public int getTailingLines() {
        return tailingLines;
    }

    @DataBoundSetter
    public void setTailingLines(int tailingLines) {
        this.tailingLines = tailingLines;
    }

    public int getSinceSeconds() {
        return sinceSeconds;
    }

    @DataBoundSetter
    public void setSinceSeconds(int sinceSeconds) {
        this.sinceSeconds = sinceSeconds;
    }

    public int getLimitBytes() {
        return limitBytes;
    }

    @DataBoundSetter
    public void setLimitBytes(int limitBytes) {
        this.limitBytes = limitBytes;
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "containerLog";
        }

        @Override
        public String getDisplayName() {
            return "Get container log from Kubernetes";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        @Override
        public boolean isAdvanced() {
            return false;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Node.class, FilePath.class, TaskListener.class);
        }
    }
}
