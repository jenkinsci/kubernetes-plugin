package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Label;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.YamlMergeStrategy;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.RetryableDeclarativeAgent;

public class KubernetesDeclarativeAgent extends RetryableDeclarativeAgent<KubernetesDeclarativeAgent> {


    private static final long serialVersionUID = 42L;

    private static final Logger LOGGER = Logger.getLogger(KubernetesDeclarativeAgent.class.getName());

    @CheckForNull
    private String label;
    @CheckForNull
    private String customWorkspace;

    @CheckForNull
    private String cloud;
    @CheckForNull
    private String inheritFrom;

    private int idleMinutes;
    private int instanceCap = Integer.MAX_VALUE;
    @CheckForNull
    private String serviceAccount;
    @CheckForNull
    private String schedulerName;
    @CheckForNull
    private String nodeSelector;
    @CheckForNull
    private String namespace;
    @CheckForNull
    private String workingDir;
    private int activeDeadlineSeconds;
    private int slaveConnectTimeout;
    @CheckForNull
    private PodRetention podRetention;

    private ContainerTemplate containerTemplate;
    private List<ContainerTemplate> containerTemplates;
    @CheckForNull
    private String defaultContainer;
    @CheckForNull
    private String yaml;
    @CheckForNull
    private String yamlFile;
    @CheckForNull
    private Boolean showRawYaml;
    private YamlMergeStrategy yamlMergeStrategy;
    @CheckForNull
    private WorkspaceVolume workspaceVolume;
    @CheckForNull
    private String supplementalGroups;

    @DataBoundConstructor
    public KubernetesDeclarativeAgent() {
    }

    @Deprecated
    public KubernetesDeclarativeAgent(String label, ContainerTemplate containerTemplate) {
        this.label = label;
        this.containerTemplate = containerTemplate;
    }

    public String getLabel() {
        return label;
    }

    public String getLabelExpression() {
        return label != null ? Label.parse(label).stream().map(Objects::toString).sorted().collect(Collectors.joining(" && ")) : null;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = Util.fixEmpty(label);
    }

    @CheckForNull
    public String getCustomWorkspace() {
        return customWorkspace;
    }

    @DataBoundSetter
    public void setCustomWorkspace(String customWorkspace) {
        this.customWorkspace = customWorkspace;
    }

    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setCloud(String cloud) {
        this.cloud = Util.fixEmpty(cloud);
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
    public void setIdleMinutes(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public String getInheritFrom() {
        return inheritFrom;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        if (PodTemplateStep.DescriptorImpl.defaultInheritFrom.equals(inheritFrom)) {
            this.inheritFrom = null;
        } else {
            this.inheritFrom = inheritFrom;
        }
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCap(int instanceCap) {
        if (instanceCap <= 0) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = instanceCap;
        }
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    @DataBoundSetter
    public void setServiceAccount(String serviceAccount) {
        this.serviceAccount = serviceAccount;
    }

    public String getSchedulerName() {
        return schedulerName;
    }

    @DataBoundSetter
    public void setSchedulerName(String schedulerName) {
        this.schedulerName = schedulerName;
    }

    public String getNodeSelector() {
        return nodeSelector;
    }

    @DataBoundSetter
    public void setNodeSelector(String nodeSelector) {
        this.nodeSelector = nodeSelector;
    }

    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public String getYaml() {
        return yaml;
    }

    @DataBoundSetter
    public void setYaml(String yaml) {
        this.yaml = yaml;
    }

    @Deprecated
    public ContainerTemplate getContainerTemplate() {
        return containerTemplate;
    }

    @DataBoundSetter
    @Restricted(DoNotUse.class)
    public void setContainerTemplate(ContainerTemplate containerTemplate) {
        this.containerTemplate = containerTemplate;
    }

    @NonNull
    public List<ContainerTemplate> getContainerTemplates() {
        return containerTemplates != null ? containerTemplates : Collections.emptyList();
    }

    @DataBoundSetter
    public void setContainerTemplates(List<ContainerTemplate> containerTemplates) {
        this.containerTemplates = containerTemplates;
    }

    public String getDefaultContainer() {
        return defaultContainer;
    }

    @DataBoundSetter
    public void setDefaultContainer(String defaultContainer) {
        this.defaultContainer = defaultContainer;
    }

    public int getActiveDeadlineSeconds() {
        return activeDeadlineSeconds;
    }

    @DataBoundSetter
    public void setActiveDeadlineSeconds(int activeDeadlineSeconds) {
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    public int getSlaveConnectTimeout() {
        return slaveConnectTimeout;
    }

    @DataBoundSetter
    public void setSlaveConnectTimeout(int slaveConnectTimeout) {
        this.slaveConnectTimeout = slaveConnectTimeout;
    }

    public PodRetention getPodRetention() {
        return this.podRetention == null ? PodTemplateStep.DescriptorImpl.defaultPodRetention : this.podRetention;
    }

    @DataBoundSetter
    public void setPodRetention(@CheckForNull PodRetention podRetention) {
        this.podRetention = (podRetention == null || podRetention.equals(PodTemplateStep.DescriptorImpl.defaultPodRetention)) ? null : podRetention;
    }

    public String getYamlFile() {
        return yamlFile;
    }

    @DataBoundSetter
    public void setShowRawYaml(Boolean showRawYaml) {
        this.showRawYaml = showRawYaml;
    }

    public Boolean getShowRawYaml() {
        return showRawYaml;
    }

    @DataBoundSetter
    public void setYamlFile(String yamlFile) {
        this.yamlFile = yamlFile;
    }

    public YamlMergeStrategy getYamlMergeStrategy() {
        return yamlMergeStrategy;
    }

    @DataBoundSetter
    public void setYamlMergeStrategy(YamlMergeStrategy yamlMergeStrategy) {
        this.yamlMergeStrategy = yamlMergeStrategy;
    }

    public WorkspaceVolume getWorkspaceVolume() {
        return workspaceVolume == null ? PodTemplateStep.DescriptorImpl.defaultWorkspaceVolume : this.workspaceVolume;
    }

    @DataBoundSetter
    public void setWorkspaceVolume(WorkspaceVolume workspaceVolume) {
        this.workspaceVolume = (workspaceVolume == null || workspaceVolume.equals(PodTemplateStep.DescriptorImpl.defaultWorkspaceVolume)) ? null : workspaceVolume;
    }

    @DataBoundSetter
    public void setSupplementalGroups(String supplementalGroups) {
        this.supplementalGroups = Util.fixEmpty(supplementalGroups);
    }

    public String getSupplementalGroups() {
        return this.supplementalGroups;
    }

    public Map<String, Object> getAsArgs() {
        Map<String, Object> argMap = new TreeMap<>();

        if (label != null) {
            argMap.put("label", label);
        }
        List<ContainerTemplate> containerTemplates = getContainerTemplates();
        if (containerTemplate != null) {
            LOGGER.log(Level.WARNING,
                    "containerTemplate option in declarative pipeline is deprecated, use yaml syntax to define containers");
            if (containerTemplates.isEmpty()) {
                containerTemplates = Collections.singletonList(containerTemplate);
            } else {
                LOGGER.log(Level.WARNING,
                        "Ignoring containerTemplate option as containerTemplates is also defined");
            }
        }
        if (!containerTemplates.isEmpty()) {
            argMap.put("containers", containerTemplates);
        }

        if (!StringUtils.isEmpty(yaml)) {
            argMap.put("yaml", yaml);
        }
        if (showRawYaml != null) {
            argMap.put("showRawYaml", showRawYaml);
        }
        if (yamlMergeStrategy != null) {
            argMap.put("yamlMergeStrategy", yamlMergeStrategy);
        }
        if (workspaceVolume != null) {
            argMap.put("workspaceVolume", workspaceVolume);
        }
        if (!StringUtils.isEmpty(cloud)) {
            argMap.put("cloud", cloud);
        }
        if (idleMinutes != 0) {
            argMap.put("idleMinutes", idleMinutes);
        }
        if (inheritFrom != null) {
            argMap.put("inheritFrom", inheritFrom);
        }
        if (!StringUtils.isEmpty(serviceAccount)) {
            argMap.put("serviceAccount", serviceAccount);
        }
        if (!StringUtils.isEmpty(nodeSelector)) {
            argMap.put("nodeSelector", nodeSelector);
        }
        if (!StringUtils.isEmpty(namespace)) {
            argMap.put("namespace", namespace);
        }
        if (!StringUtils.isEmpty(workingDir)) {
            argMap.put("workingDir", workingDir);
        }
        if (activeDeadlineSeconds != 0) {
            argMap.put("activeDeadlineSeconds", activeDeadlineSeconds);
        }
        if (slaveConnectTimeout != 0) {
            argMap.put("slaveConnectTimeout", slaveConnectTimeout);
        }
        if (podRetention != null) {
            argMap.put("podRetention", podRetention);
        }
        if (instanceCap > 0 && instanceCap < Integer.MAX_VALUE) {
            argMap.put("instanceCap", instanceCap);
        }
        if (!StringUtils.isEmpty(supplementalGroups)){
            argMap.put("supplementalGroups", supplementalGroups);
        }

        return argMap;
    }

    @OptionalExtension(requirePlugins = "pipeline-model-extensions")
    @Symbol("kubernetes")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<KubernetesDeclarativeAgent> {

        static final String[] POD_TEMPLATE_FIELDS = {"namespace", "inheritFrom", "yaml", "showRawYaml", "instanceCap", "podRetention", "supplementalGroups", "idleMinutes", "activeDeadlineSeconds", "serviceAccount", "nodeSelector", "workingDir", "workspaceVolume"};

        public DescriptorImpl() {
            for (String field: new String[] {"cloud", "label"}) {
                addHelpFileRedirect(field, PodTemplateStep.class, field);
            }
            for (String field: POD_TEMPLATE_FIELDS) {
                addHelpFileRedirect(field, PodTemplate.class, field);
            }
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.KubernetesDeclarativeAgent_displayName();
        }

        @SuppressWarnings("unused") // by stapler/jelly
        public ListBoxModel doFillCloudItems() {
            return ExtensionList.lookupSingleton(PodTemplateStep.DescriptorImpl.class).doFillCloudItems();
        }

        @SuppressWarnings("unused") // by stapler/jelly
        public ListBoxModel doFillInheritFromItems(@QueryParameter("cloud") String cloudName) {
            return ExtensionList.lookupSingleton(PodTemplateStep.DescriptorImpl.class).doFillInheritFromItems(cloudName);
        }

        public PodRetention getDefaultPodRetention() {
            return PodRetention.getPodTemplateDefault();
        }

        public WorkspaceVolume getDefaultWorkspaceVolume() {
            return WorkspaceVolume.getDefault();
        }
    }
}
