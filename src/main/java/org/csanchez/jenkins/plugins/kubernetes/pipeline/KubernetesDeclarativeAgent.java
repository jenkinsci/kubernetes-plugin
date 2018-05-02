package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubernetesDeclarativeAgent extends DeclarativeAgent<KubernetesDeclarativeAgent> {

    private static final Logger LOGGER = Logger.getLogger(KubernetesDeclarativeAgent.class.getName());

    private String label;

    private String cloud;
    private String inheritFrom;

    private int instanceCap;
    private String serviceAccount;
    private String nodeSelector;
    private String workingDir;
    private int activeDeadlineSeconds;

    private ContainerTemplate containerTemplate;
    private List<ContainerTemplate> containerTemplates;
    private String defaultContainer;
    private String yaml;

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

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
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

    public Map<String, Object> getAsArgs() {
        Map<String, Object> argMap = new TreeMap<>();

        argMap.put("label", label);
        argMap.put("name", label);

        List<ContainerTemplate> containerTemplates = getContainerTemplates();
        if (containerTemplate != null) {
            LOGGER.log(Level.WARNING,
                    "containerTemplate option in declarative pipeline is deprecated, use containerTemplates");
            if (containerTemplates.isEmpty()) {
                containerTemplates = Collections.singletonList(containerTemplate);
            } else {
                LOGGER.log(Level.WARNING,
                        "Ignoring containerTemplate option as containerTemplates is also defined");
            }
        }
        argMap.put("containers", containerTemplates);

        if (!StringUtils.isEmpty(yaml)) {
            argMap.put("yaml", yaml);
        }
        if (!StringUtils.isEmpty(cloud)) {
            argMap.put("cloud", cloud);
        }
        if (!StringUtils.isEmpty(inheritFrom)) {
            argMap.put("inheritFrom", inheritFrom);
        }
        if (!StringUtils.isEmpty(serviceAccount)) {
            argMap.put("serviceAccount", serviceAccount);
        }
        if (!StringUtils.isEmpty(nodeSelector)) {
            argMap.put("nodeSelector", nodeSelector);
        }
        if (!StringUtils.isEmpty(workingDir)) {
            argMap.put("workingDir", workingDir);
        }
        if (activeDeadlineSeconds != 0) {
            argMap.put("activeDeadlineSeconds", activeDeadlineSeconds);
        }

        if (instanceCap > 0) {
            argMap.put("instanceCap", instanceCap);
        }

        return argMap;
    }

    @OptionalExtension(requirePlugins = "pipeline-model-extensions")
    @Symbol("kubernetes")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<KubernetesDeclarativeAgent> {
    }
}
