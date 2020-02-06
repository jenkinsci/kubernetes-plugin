package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Always;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.YamlMergeStrategy;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import javax.annotation.CheckForNull;

public class PodTemplateStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private static final String DEFAULT_CLOUD = "kubernetes";

    @CheckForNull
    private String cloud = DEFAULT_CLOUD;

    @CheckForNull
    private String inheritFrom;

    @CheckForNull
    private String label;

    @CheckForNull
    private String name;

    @CheckForNull
    private String namespace;

    @CheckForNull
    private List<ContainerTemplate> containers = new ArrayList<>();

    @CheckForNull
    private List<TemplateEnvVar> envVars = new ArrayList<>();

    @CheckForNull
    private List<PodVolume> volumes = new ArrayList<PodVolume>();

    @CheckForNull
    private WorkspaceVolume workspaceVolume;

    @CheckForNull
    private List<PodAnnotation> annotations = new ArrayList<>();

    @CheckForNull
    private List<String> imagePullSecrets = new ArrayList<>();

    private int instanceCap;
    private int idleMinutes;
    private int slaveConnectTimeout = PodTemplate.DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;
    private int activeDeadlineSeconds;

    private Boolean hostNetwork;

    @CheckForNull
    private String serviceAccount;

    @CheckForNull
    private String nodeSelector;

    @CheckForNull
    private Node.Mode nodeUsageMode = Node.Mode.EXCLUSIVE;

    @CheckForNull
    private String workingDir = ContainerTemplate.DEFAULT_WORKING_DIR;

    @CheckForNull
    private String yaml;

    @CheckForNull
    private YamlMergeStrategy yamlMergeStrategy = YamlMergeStrategy.defaultStrategy();

    @CheckForNull
    private PodRetention podRetention;

    private Boolean showRawYaml;


    @CheckForNull
    private String runAsUser;

    @CheckForNull
    private String runAsGroup;

    @CheckForNull
    private String supplementalGroups;

    @DataBoundConstructor
    public PodTemplateStep() {}

    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setLabel(@CheckForNull String label) {
        this.label = Util.fixEmpty(label);
    }

    @CheckForNull
    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(@CheckForNull String name) {
        this.name = Util.fixEmpty(name);
    }

    @CheckForNull
    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(@CheckForNull String namespace) {
        this.namespace = Util.fixEmpty(namespace);
    }

    @CheckForNull
    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setCloud(@CheckForNull String cloud) {
        this.cloud = Util.fixEmpty(cloud);
    }

    @CheckForNull
    public String getInheritFrom() {
        return inheritFrom;
    }

    @DataBoundSetter
    public void setInheritFrom(@CheckForNull String inheritFrom) {
        this.inheritFrom = Util.fixEmpty(inheritFrom);
    }

    @CheckForNull
    public List<ContainerTemplate> getContainers() {
        return containers;
    }

    @DataBoundSetter
    public void setContainers(@CheckForNull List<ContainerTemplate> containers) {
        this.containers = Util.fixNull(containers);
    }

    @CheckForNull
    public List<TemplateEnvVar> getEnvVars() {
        return envVars == null ? Collections.emptyList() : envVars;
    }

    @DataBoundSetter
    public void setEnvVars(@CheckForNull List<TemplateEnvVar> envVars) {
        if (envVars != null) {
            this.envVars.clear();
            this.envVars.addAll(envVars);
        }
    }

    @CheckForNull
    public YamlMergeStrategy getYamlMergeStrategy() {
        return yamlMergeStrategy;
    }

    @DataBoundSetter
    public void setYamlMergeStrategy(@CheckForNull YamlMergeStrategy yamlMergeStrategy) {
        this.yamlMergeStrategy = yamlMergeStrategy;
    }

    @CheckForNull
    public List<PodVolume> getVolumes() {
        return volumes;
    }

    @DataBoundSetter
    public void setVolumes(@CheckForNull List<PodVolume> volumes) {
        this.volumes = Util.fixNull(volumes);
    }

    @CheckForNull
    public WorkspaceVolume getWorkspaceVolume() {
        return workspaceVolume == null ? DescriptorImpl.defaultWorkspaceVolume : this.workspaceVolume;
    }

    @DataBoundSetter
    public void setWorkspaceVolume(@CheckForNull WorkspaceVolume workspaceVolume) {
        this.workspaceVolume = (workspaceVolume == null || workspaceVolume.equals(DescriptorImpl.defaultWorkspaceVolume)) ? null : workspaceVolume;
    }

    public int getInstanceCap() {
        return instanceCap;// == null ? DescriptorImpl.defaultInstanceCap : instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCap(int instanceCap) {
        this.instanceCap = instanceCap;
/*        if (instanceCap == null || instanceCap.intValue() <= 0) {
            this.instanceCap = null;
        } else {
            this.instanceCap = instanceCap.equals(DescriptorImpl.defaultInstanceCap) ? null : instanceCap;
        }*/
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
    public void setIdleMinutes(@CheckForNull int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    @CheckForNull
    public int getSlaveConnectTimeout() {
        return slaveConnectTimeout;
    }

    @DataBoundSetter
    public void setSlaveConnectTimeout(@CheckForNull int slaveConnectTimeout) {
        this.slaveConnectTimeout = slaveConnectTimeout;
    }

    @CheckForNull
    public int getActiveDeadlineSeconds() {
        return activeDeadlineSeconds;
    }

    @DataBoundSetter
    public void setActiveDeadlineSeconds(@CheckForNull int activeDeadlineSeconds) {
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    public Boolean getHostNetwork() {
        return hostNetwork;
    }

    @DataBoundSetter
    public void setHostNetwork(boolean hostNetwork) {
        this.hostNetwork = hostNetwork;
    }

    @CheckForNull
    public String getServiceAccount() { return serviceAccount; }

    @DataBoundSetter
    public void setServiceAccount(@CheckForNull String serviceAccount) {
        this.serviceAccount = Util.fixEmpty(serviceAccount);
    }

    @CheckForNull
    public String getNodeSelector() {
        return nodeSelector;
    }

    @DataBoundSetter
    public void setNodeSelector(@CheckForNull String nodeSelector) {
        this.nodeSelector = Util.fixEmpty(nodeSelector);
    }

    public Node.Mode getNodeUsageMode() {
        return nodeUsageMode;
    }

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

    @CheckForNull
    public String getYaml() {
        return yaml;
    }

    @DataBoundSetter
    public void setYaml(@CheckForNull String yaml) {
        this.yaml = Util.fixEmpty(yaml);
    }

    @CheckForNull
    public PodRetention getPodRetention() {
        return this.podRetention == null ? DescriptorImpl.defaultPodRetention : this.podRetention;
    }

    @DataBoundSetter
    public void setPodRetention(@CheckForNull PodRetention podRetention) {
        this.podRetention = (podRetention == null || podRetention.equals(DescriptorImpl.defaultPodRetention)) ? null : podRetention;
    }

    boolean isShowRawYamlSet() {
        return showRawYaml != null;
    }

    public boolean isShowRawYaml() {
        return isShowRawYamlSet() ? showRawYaml.booleanValue() : true;
    }

    @DataBoundSetter
    public void setShowRawYaml(boolean showRawYaml) {
        this.showRawYaml = Boolean.valueOf(showRawYaml);
    }

    public String getRunAsUser(){
        return this.runAsUser;
    }

    @DataBoundSetter
    public void setRunAsUser(String runAsUser) {
        this.runAsUser = runAsUser;
    }

    public String getRunAsGroup(){
        return this.runAsGroup;
    }

    @DataBoundSetter
    public void setRunAsGroup(String runAsGroup) {
        this.runAsGroup = runAsGroup;
    }

    @CheckForNull
    public String getSupplementalGroups() {
        return supplementalGroups;
    }

    @DataBoundSetter
    public void setSupplementalGroups(@CheckForNull String supplementalGroups) {
        this.supplementalGroups = Util.fixEmpty(supplementalGroups);
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
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }

        @SuppressWarnings("unused") // jelly
        public String getWorkingDir() {
            return ContainerTemplate.DEFAULT_WORKING_DIR;
        }

        public static final PodRetention defaultPodRetention = new Always();
        public static final WorkspaceVolume defaultWorkspaceVolume = new DynamicPVCWorkspaceVolume(null, null, "ReadWriteOnce");
    }
}
