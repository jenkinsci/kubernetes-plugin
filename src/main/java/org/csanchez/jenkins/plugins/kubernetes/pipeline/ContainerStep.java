package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.TaskListener;

public class ContainerStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;
    private String shell;

    @DataBoundConstructor
    public ContainerStep(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setShell(String shell){
        this.shell = Util.fixEmpty(shell);
    }

    public String getShell() {
        return shell;
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
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Node.class, FilePath.class, TaskListener.class)));
        }
    }
}
