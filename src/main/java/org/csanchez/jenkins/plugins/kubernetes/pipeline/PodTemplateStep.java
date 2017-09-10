package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
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
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class PodTemplateStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private static final String DEFAULT_CLOUD = "kubernetes";

    private String cloud = DEFAULT_CLOUD;
    private String inheritFrom;

    private final String label;
    private final String name;

    private String namespace;
    private List<ContainerTemplate> containers = new ArrayList<>();
    private List<TemplateEnvVar> envVars = new ArrayList<>();
    private List<PodVolume> volumes = new ArrayList<PodVolume>();
    private WorkspaceVolume workspaceVolume;
    private List<PodAnnotation> annotations = new ArrayList<>();
    private List<String> imagePullSecrets = new ArrayList<>();

    private int instanceCap = Integer.MAX_VALUE;
    private int idleMinutes;
    private int slaveConnectTimeout;

    private String serviceAccount;
    private String nodeSelector;
    private Node.Mode nodeUsageMode;
    private String workingDir = ContainerTemplate.DEFAULT_WORKING_DIR;

    @DataBoundConstructor
    public PodTemplateStep(String label, String name) {
        this.label = label;
        this.name = name == null ? "jenkins-slave" : name;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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

    public List<TemplateEnvVar> getEnvVars() {
        return envVars == null ? Collections.emptyList() : envVars;
    }

    @DataBoundSetter
    public void setEnvVars(List<TemplateEnvVar> envVars) {
        if (envVars != null) {
            this.envVars.clear();
            this.envVars.addAll(envVars);
        }
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

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
    public void setIdleMinutes(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public int getSlaveConnectTimeout() {
        return slaveConnectTimeout;
    }

    @DataBoundSetter
    public void setSlaveConnectTimeout(int slaveConnectTimeout) {
        this.slaveConnectTimeout = slaveConnectTimeout;
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

    public Node.Mode getNodeUsageMode() {
        return nodeUsageMode;
    }

    @DataBoundSetter
    public void setNodeUsageMode(Node.Mode nodeUsageMode) {
        this.nodeUsageMode = nodeUsageMode;
    }

    @DataBoundSetter
    public void setNodeUsageMode(String nodeUsageMode) {
        this.nodeUsageMode = Node.Mode.valueOf(nodeUsageMode);
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

    public List<String> getImagePullSecrets() {
        return imagePullSecrets == null ? Collections.emptyList() : imagePullSecrets;
    }

    @DataBoundSetter
    public void setImagePullSecrets(List<String> imagePullSecrets) {
        if (imagePullSecrets != null) {
            this.imagePullSecrets.clear();
            this.imagePullSecrets.addAll(imagePullSecrets);
        }
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
