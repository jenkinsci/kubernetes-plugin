package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;

public class ContainerStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;

    @DataBoundConstructor
    public ContainerStep(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ContainerStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "container";
        }

        @Override
        public String getDisplayName() {
            return "Run build steps in a container";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(PodTemplateStep.class, FilePath.class, TaskListener.class);
        }
    }
}
