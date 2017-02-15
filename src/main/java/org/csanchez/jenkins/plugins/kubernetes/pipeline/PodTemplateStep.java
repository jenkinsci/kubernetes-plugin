package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;

public class PodTemplateStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private static final String DEFAULT_CLOUD = "kubernetes";

    private String cloud = DEFAULT_CLOUD;
    private String inheritFrom;

    private final String label;
    private final String name;

    private List<ContainerTemplate> containers = new ArrayList<>();
    private List<PodVolume> volumes = new ArrayList<PodVolume>();
    private WorkspaceVolume workspaceVolume;
    private List<PodAnnotation> annotations = new ArrayList<>();

    private int instanceCap;
    private String serviceAccount;
    private String nodeSelector;
    private String workingDir = ContainerTemplate.DEFAULT_WORKING_DIR;

    @DataBoundConstructor
    public PodTemplateStep(String label, String name) {
        this.label = label;
        this.name = name == null ? "kubernetes" : name;
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

    public List<PodVolume> getVolumes() {
        return volumes;
    }

    @DataBoundSetter
    public void setVolumes(List<PodVolume> volumes) {
        this.volumes = volumes;
    }

    public WorkspaceVolume getWorkspaceVolume() {
        return workspaceVolume;
    }

    @DataBoundSetter
    public void setWorkspaceVolume(WorkspaceVolume workspaceVolume) {
        this.workspaceVolume = workspaceVolume;
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCap(int instanceCap) {
        this.instanceCap = instanceCap;
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

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new PodTemplateStepExecution(this, context);
    }

    public List<PodAnnotation> getAnnotations() {
        return annotations;
    }

    @DataBoundSetter
    public void setAnnotations(List<PodAnnotation> annotations) {
        this.annotations = annotations;
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

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

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }
    }
}
