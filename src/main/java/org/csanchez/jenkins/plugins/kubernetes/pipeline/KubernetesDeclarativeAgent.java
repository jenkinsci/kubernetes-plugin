package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgent;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class KubernetesDeclarativeAgent extends DeclarativeAgent<KubernetesDeclarativeAgent> {
    private final String label;

    private String cloud;
    private String inheritFrom;

    private int instanceCap;
    private String serviceAccount;
    private String nodeSelector;
    private String workingDir;
    private int deadlineSeconds;

    private ContainerTemplate containerTemplate;

    @DataBoundConstructor
    public KubernetesDeclarativeAgent(String label, ContainerTemplate containerTemplate) {
        this.label = label;
        this.containerTemplate = containerTemplate;
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

    public int getDeadlineSeconds() {
        return deadlineSeconds;
    }

    @DataBoundSetter
    public void setDeadlineSeconds(int deadlineSeconds) {
        this.deadlineSeconds = deadlineSeconds;
    }

    public Map<String,Object> getAsArgs() {
        Map<String,Object> argMap = new TreeMap<>();

        argMap.put("label", label);
        argMap.put("name", label);
        argMap.put("containers", Collections.singletonList(containerTemplate));

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
        if (deadlineSeconds != 0) {
            argMap.put("deadlineSeconds", deadlineSeconds);
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
