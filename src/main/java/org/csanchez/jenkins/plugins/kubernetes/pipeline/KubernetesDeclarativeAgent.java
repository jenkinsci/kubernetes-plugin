package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.PodImagePullSecret;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class KubernetesDeclarativeAgent extends DeclarativeAgent<KubernetesDeclarativeAgent> {
    private final String label;

    private String cloud;
    private String inheritFrom;

    private int instanceCap;
    private String serviceAccount;
    private String nodeSelector;
    private String workingDir;
    private int activeDeadlineSeconds;

    private ContainerTemplate containerTemplate;
    private PodTemplate podTemplate;
    private String podTemplateName;

    @Deprecated
    public KubernetesDeclarativeAgent(String label, ContainerTemplate containerTemplate) {
        this.label = label;
        this.containerTemplate = containerTemplate;
    }

    @DataBoundConstructor
    public KubernetesDeclarativeAgent(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
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

    public ContainerTemplate getContainerTemplate() {
        return containerTemplate;
    }

    @DataBoundSetter
    public void setContainerTemplate(ContainerTemplate containerTemplate) {
        this.containerTemplate = containerTemplate;
    }

    public PodTemplate getPodTemplate() {
        return podTemplate;
    }

    @DataBoundSetter
    public void setPodTemplate(PodTemplate podTemplate) {
        this.podTemplate = podTemplate;
        List<ContainerTemplate> containers = podTemplate.getContainers();
        if (containers != null && containers.size() > 0) {
            this.containerTemplate = containers.get(0);
        }
    }

    public int getActiveDeadlineSeconds() {
        return activeDeadlineSeconds;
    }

    @DataBoundSetter
    public void setActiveDeadlineSeconds(int activeDeadlineSeconds) { this.activeDeadlineSeconds = activeDeadlineSeconds; }

    public String getPodTemplateName() {
        return podTemplateName;
    }

    @DataBoundSetter
    public void setPodTemplateName(String podTemplateName) {
        this.podTemplateName = podTemplateName;
        if (podTemplateName != null) {
            String cloudName = getCloud();
            if (StringUtils.isEmpty(cloudName)) {
                cloudName = KubernetesCloud.CLOUD_NAME;
            }
            Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
            if (cloud instanceof KubernetesCloud) {
                KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
                List<PodTemplate> templates = kubernetesCloud.getTemplates();
                PodTemplate template = null;
                for (PodTemplate t : templates) {
                    String name = t.getName();
                    if (podTemplateName.equals(name)) {
                        template = t;
                        break;
                    }
                }
                if (template != null) {
                    setPodTemplate(template);
                }
            }
        }
    }

    public Map<String,Object> getAsArgs() {
        Map<String,Object> argMap = new TreeMap<>();

        int instanceCap = this.instanceCap;
        int activeDeadlineSeconds = this.activeDeadlineSeconds;
        String inheritFrom = this.inheritFrom;
        String nodeSelector = this.nodeSelector;
        String serviceAccount = this.serviceAccount;

        String label = this.label;
        if (podTemplate != null) {
            if (StringUtils.isEmpty(label)) {
                label = podTemplate.getLabel();
                if (StringUtils.isEmpty(label)) {
                    label = podTemplate.getName();
                }
            }
            // override properties if specified on the podTemplate
            if (podTemplate.getInstanceCap() > 0) {
                instanceCap = podTemplate.getInstanceCap();
            }
            if (podTemplate.getActiveDeadlineSeconds() > 0) {
                activeDeadlineSeconds = podTemplate.getActiveDeadlineSeconds();
            }
            if (!StringUtils.isEmpty(podTemplate.getInheritFrom())) {
                inheritFrom = podTemplate.getInheritFrom();
            }
            if (!StringUtils.isEmpty(podTemplate.getNodeSelector())) {
                nodeSelector = podTemplate.getNodeSelector();
            }
            if (!StringUtils.isEmpty(podTemplate.getServiceAccount())) {
                serviceAccount = podTemplate.getServiceAccount();
            }


            argMap.put("containers", podTemplate.getContainers());

            List<PodAnnotation> annotations = podTemplate.getAnnotations();
            if (annotations != null && annotations.size() > 0) {
                argMap.put("annotations", annotations);
            }
            Set<LabelAtom> labelSet = podTemplate.getLabelSet();
            if (labelSet != null && labelSet.size() > 0) {
                argMap.put("labelSet", labelSet);
            }
            List<TemplateEnvVar> envVars = podTemplate.getEnvVars();
            if (envVars != null && envVars.size() > 0) {
                argMap.put("envVars", envVars);
            }
            List<PodImagePullSecret> imagePullSecrets = podTemplate.getImagePullSecrets();
            if (imagePullSecrets != null && imagePullSecrets.size() > 0) {
                argMap.put("imagePullSecrets", imagePullSecrets);
            }
            List<PodVolume> volumes = podTemplate.getVolumes();
            if (volumes != null && volumes.size() > 0) {
                argMap.put("volumes", volumes);
            }
        } else if (containerTemplate != null) {
            argMap.put("containers", Collections.singletonList(containerTemplate));
        }
        argMap.put("label", label);
        argMap.put("name", label);
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

    @OptionalExtension(requirePlugins = "pipeline-model-extensions") @Symbol("kubernetes")
    public static class DescriptorImpl extends DeclarativeAgentDescriptor<KubernetesDeclarativeAgent> {
    }
}
