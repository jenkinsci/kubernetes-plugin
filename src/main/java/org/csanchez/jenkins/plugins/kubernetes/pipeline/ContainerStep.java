package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class ContainerStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private final String pod;
    private final String name;

    @DataBoundConstructor
    public ContainerStep(String pod, String name) {
        this.pod = pod;
        this.name = name;
    }

    public String getPod() {
        return pod;
    }

    public String getName() {
        return name;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(ContainerStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

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
    }
}
