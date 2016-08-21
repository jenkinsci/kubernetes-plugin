package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodVolumes;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;

public class PodTemplateStep extends AbstractStepImpl implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private static final String DEFAULT_CLOUD = "kubernetes";
    private static final String DEFAULT_WORKING_DIR = "/home/jenkins";

    private String cloud = DEFAULT_CLOUD;
    private String inheritFrom;

    private final String label;
    private final String name;

    private List<ContainerTemplate> containers = new ArrayList<ContainerTemplate>();
    private List<PodVolumes.PodVolume> volumes = new ArrayList<PodVolumes.PodVolume>();

    private String serviceAccount;
    private String nodeSelector;
    private String workingDir = DEFAULT_WORKING_DIR;

    @DataBoundConstructor
    public PodTemplateStep(String label, String name) {
        this.label = label;
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }


    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getInheritFrom() {
        return inheritFrom;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom;
    }

    public List<ContainerTemplate> getContainers() {
        return containers;
    }

    @DataBoundSetter
    public void setContainers(List<ContainerTemplate> containers) {
        this.containers = containers;
    }

    public List<PodVolumes.PodVolume> getVolumes() {
        return volumes;
    }

    @DataBoundSetter
    public void setVolumes(List<PodVolumes.PodVolume> volumes) {
        this.volumes = volumes;
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    @DataBoundSetter
    public void setServiceAccount(String serviceAccount) {
        this.serviceAccount = serviceAccount;
    }

    public String getNodeSelector() {
        return nodeSelector;
    }

    @DataBoundSetter
    public void setNodeSelector(String nodeSelector) {
        this.nodeSelector = nodeSelector;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

       public DescriptorImpl() {
            super(PodTemplateStepExecution.class);
        }

        public DescriptorImpl(Class<? extends StepExecution> executionType) {
            super(executionType);
        }

        @Override
        public String getFunctionName() {
            return "podTemplate";
        }

        @Override
        public String getDisplayName() {
            return "Define a podTemplate to use in the kubernetes plugin";
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
