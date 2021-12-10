package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.slaves.Cloud;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.YamlMergeStrategy;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
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
import org.kohsuke.stapler.QueryParameter;

public class PodTemplateStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    @CheckForNull
    private String cloud;

    @CheckForNull
    private String inheritFrom;

    @CheckForNull
    private String label;

    @CheckForNull
    private String name;

    @CheckForNull
    private String namespace;

    private List<ContainerTemplate> containers = new ArrayList<>();
    private List<TemplateEnvVar> envVars = new ArrayList<>();
    private List<PodVolume> volumes = new ArrayList<PodVolume>();

    @CheckForNull
    private WorkspaceVolume workspaceVolume;

    private List<PodAnnotation> annotations = new ArrayList<>();
    private List<String> imagePullSecrets = new ArrayList<>();

    private Integer instanceCap = Integer.MAX_VALUE;
    private int idleMinutes;
    private int slaveConnectTimeout = PodTemplate.DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;
    private int activeDeadlineSeconds;

    private Boolean hostNetwork;

    @CheckForNull
    private String serviceAccount;

    @CheckForNull
    private String schedulerName;

    @CheckForNull
    private String nodeSelector;

    private Node.Mode nodeUsageMode = Node.Mode.EXCLUSIVE;
    private String workingDir = ContainerTemplate.DEFAULT_WORKING_DIR;

    @CheckForNull
    private String yaml;

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
        if (DescriptorImpl.defaultInheritFrom.equals(inheritFrom)) {
            this.inheritFrom = null;
        } else {
            this.inheritFrom = inheritFrom;
        }
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

    @CheckForNull
    public YamlMergeStrategy getYamlMergeStrategy() {
        return yamlMergeStrategy;
    }

    @DataBoundSetter
    public void setYamlMergeStrategy(YamlMergeStrategy yamlMergeStrategy) {
        this.yamlMergeStrategy = yamlMergeStrategy;
    }

    public List<PodVolume> getVolumes() {
        return volumes;
    }

    @DataBoundSetter
    public void setVolumes(List<PodVolume> volumes) {
        this.volumes = volumes;
    }

    @CheckForNull
    public WorkspaceVolume getWorkspaceVolume() {
        return workspaceVolume == null ? DescriptorImpl.defaultWorkspaceVolume : this.workspaceVolume;
    }

    @DataBoundSetter
    public void setWorkspaceVolume(@CheckForNull WorkspaceVolume workspaceVolume) {
        this.workspaceVolume = (workspaceVolume == null || workspaceVolume.equals(DescriptorImpl.defaultWorkspaceVolume)) ? null : workspaceVolume;
    }

    public Integer getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCap(@CheckForNull Integer instanceCap) {
        if (instanceCap == null || instanceCap.intValue() <= 0) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = instanceCap;
        }
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
    public String getSchedulerName() { return schedulerName; }

    @DataBoundSetter
    public void setSchedulerName(@CheckForNull String schedulerName) {
        this.schedulerName = Util.fixEmpty(schedulerName);
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

        static final String[] POD_TEMPLATE_FIELDS = {"name", "namespace", "inheritFrom", "containers", "envVars", "volumes", "annotations", "yaml", "showRawYaml", "instanceCap", "podRetention", "supplementalGroups", "idleMinutes", "activeDeadlineSeconds", "serviceAccount", "nodeSelector", "workingDir", "workspaceVolume"};

        public DescriptorImpl() {
            for (String field : POD_TEMPLATE_FIELDS) {
                addHelpFileRedirect(field, PodTemplate.class, field);
            }
        }

        @SuppressWarnings("unused") // by stapler/jelly
        public ListBoxModel doFillCloudItems() {
            ListBoxModel result = new ListBoxModel();
            result.add("—any—", "");
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) { // TODO track use of SYSTEM_READ and/or MANAGE in GlobalCloudConfiguration
                return result;
            }
            Jenkins.get().clouds
                    .getAll(KubernetesCloud.class)
                    .forEach(cloud -> result.add(cloud.name));
            return result;
        }

        @SuppressWarnings("unused") // by stapler/jelly
        public ListBoxModel doFillInheritFromItems(@QueryParameter("cloud") String cloudName) {
            cloudName = Util.fixEmpty(cloudName);
            ListBoxModel result = new ListBoxModel();
            result.add("—Default inheritance—", "<default>");
            result.add("—Disable inheritance—", " ");
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) { // TODO track use of SYSTEM_READ and/or MANAGE in GlobalCloudConfiguration
                return result;
            }
            Cloud cloud;
            if (cloudName == null) {
                cloud = Jenkins.get().clouds.get(KubernetesCloud.class);
            } else {
                cloud = Jenkins.get().getCloud(cloudName);
            }
            if (cloud instanceof KubernetesCloud) {
                List<PodTemplate> templates = ((KubernetesCloud) cloud).getTemplates();
                result.addAll(templates.stream()
                        .filter(template -> StringUtils.isNotEmpty(template.getName()))
                        .map(PodTemplate::getName)
                        .map(ListBoxModel.Option::new)
                        .collect(Collectors.toList()));
            }
            return result;
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
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Run.class, TaskListener.class)));
        }

        @SuppressWarnings("unused") // jelly
        public String getWorkingDir() {
            return ContainerTemplate.DEFAULT_WORKING_DIR;
        }

        public static final Integer defaultInstanceCap = Integer.MAX_VALUE;
        public static final PodRetention defaultPodRetention = PodRetention.getPodTemplateDefault();
        public static final WorkspaceVolume defaultWorkspaceVolume = WorkspaceVolume.getDefault();
        /** Only used for snippet generation. */
        public static final String defaultInheritFrom = "<default>";
    }
}
