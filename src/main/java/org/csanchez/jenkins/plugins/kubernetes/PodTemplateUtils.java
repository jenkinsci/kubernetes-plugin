package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import hudson.model.Label;
import hudson.tools.ToolLocationNodeProperty;

import static org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate.DEFAULT_WORKING_DIR;

public class PodTemplateUtils {

    /**
     * Combines a {@link ContainerTemplate} with its parent.
     * @param parent        The parent container template (nullable).
     * @param template      The actual container template
     * @return              The combined container template.
     */
    public static ContainerTemplate combine(ContainerTemplate parent, ContainerTemplate template) {
        Preconditions.checkNotNull(template, "Container template should not be null");
        if (parent == null) {
            return template;
        }

        String name = template.getName();
        String image = Strings.isNullOrEmpty(template.getImage()) ? parent.getImage() : template.getImage();
        Boolean privileged = template.isPrivileged() != null ? template.isPrivileged() : (parent.isPrivileged() != null ? parent.isPrivileged() : false);
        Boolean alwaysPullImage = template.isAlwaysPullImage() != null ? template.isAlwaysPullImage() : (parent.isAlwaysPullImage() != null ? parent.isAlwaysPullImage() : false);
        String workingDir = Strings.isNullOrEmpty(template.getWorkingDir()) ? (Strings.isNullOrEmpty(parent.getWorkingDir()) ? DEFAULT_WORKING_DIR : parent.getWorkingDir()) : template.getCommand();
        String command = Strings.isNullOrEmpty(template.getCommand()) ? parent.getCommand() : template.getCommand();
        String args = Strings.isNullOrEmpty(template.getArgs()) ? parent.getArgs() : template.getArgs();
        Boolean ttyEnabled = template.isTtyEnabled() != null ? template.isTtyEnabled() : (parent.isTtyEnabled() != null ? parent.isTtyEnabled() : false);;
        String resourceRequestCpu = Strings.isNullOrEmpty(template.getResourceRequestCpu()) ? parent.getResourceRequestCpu() : template.getResourceRequestCpu();
        String resourceRequestMemory = Strings.isNullOrEmpty(template.getResourceRequestMemory()) ? parent.getResourceRequestMemory() : template.getResourceRequestMemory();
        String resourceLimitCpu = Strings.isNullOrEmpty(template.getResourceLimitCpu()) ? parent.getResourceLimitCpu() : template.getResourceLimitCpu();
        String resourceLimitMemory = Strings.isNullOrEmpty(template.getResourceLimitMemory()) ? parent.getResourceLimitMemory() : template.getResourceLimitMemory();

        List<ContainerEnvVar> combinedEnvVars = new ArrayList<ContainerEnvVar>();

        Map<String, String> envVars = new HashMap<>();
        for (ContainerEnvVar envVar : parent.getEnvVars()) {
            envVars.put(envVar.getKey(), envVar.getValue());
        }

        for (ContainerEnvVar envVar : template.getEnvVars()) {
            envVars.put(envVar.getKey(), envVar.getValue());
        }

        for (Map.Entry<String, String> e : envVars.entrySet()) {
            combinedEnvVars.add(new ContainerEnvVar(e.getKey(), e.getValue()));
        }

        ContainerTemplate combined = new ContainerTemplate(image);
        combined.setName(name);
        combined.setImage(image);
        combined.setAlwaysPullImage(alwaysPullImage);
        combined.setCommand(command);
        combined.setArgs(args);
        combined.setTtyEnabled(ttyEnabled);
        combined.setResourceLimitCpu(resourceLimitCpu);
        combined.setResourceLimitMemory(resourceLimitMemory);
        combined.setResourceRequestCpu(resourceRequestCpu);
        combined.setResourceRequestMemory(resourceRequestMemory);
        combined.setWorkingDir(workingDir);
        combined.setPrivileged(privileged);
        combined.setEnvVars(combinedEnvVars);
        return combined;
    }

    /**
     * Combines a {@link PodTemplate} with its parent.
     * @param parent        The parent container template (nullable).
     * @param template      The actual container template
     * @return              The combined container template.
     */
    public static PodTemplate combine(PodTemplate parent, PodTemplate template) {
        Preconditions.checkNotNull(template, "Pod template should not be null");
        if (parent == null) {
            return template;
        }

        String name = template.getName();
        String label = template.getLabel();
        String nodeSelector = Strings.isNullOrEmpty(template.getNodeSelector()) ? parent.getNodeSelector() : template.getNodeSelector();
        String serviceAccount = Strings.isNullOrEmpty(template.getServiceAccount()) ? parent.getServiceAccount() : template.getServiceAccount();

        Set<PodImagePullSecret> imagePullSecrets = new LinkedHashSet<>();
        imagePullSecrets.addAll(parent.getImagePullSecrets());
        imagePullSecrets.addAll(template.getImagePullSecrets());

        Map<String, ContainerTemplate> combinedContainers = new HashMap<>();
        Map<String, PodVolume> combinedVolumes = new HashMap<>();

        //Containers
        Map<String, String> combinedEnvVars = new HashMap<>();
        combinedEnvVars.putAll(parent.getEnvVars().stream().collect(Collectors.toMap(e -> e.getKey(),e -> e.getValue())));
        combinedEnvVars.putAll(template.getEnvVars().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));

        //Containers
        Map<String, ContainerTemplate> parentContainers = parent.getContainers().stream().collect(Collectors.toMap(c -> c.getName(), c -> c));
        combinedContainers.putAll(parentContainers);
        combinedContainers.putAll(template.getContainers().stream().collect(Collectors.toMap(c -> c.getName(), c -> combine(parentContainers.get(c.getName()), c))));

        //Volumes
        Map<String, PodVolume> parentVolumes = parent.getVolumes().stream().collect(Collectors.toMap(v -> v.getMountPath(), v -> v));
        combinedVolumes.putAll(parentVolumes);
        combinedVolumes.putAll(template.getVolumes().stream().collect(Collectors.toMap(v -> v.getMountPath(), v -> v)));

        //Tool location node properties
        List<ToolLocationNodeProperty> toolLocationNodeProperties = new ArrayList<>();
        toolLocationNodeProperties.addAll(parent.getNodeProperties());
        toolLocationNodeProperties.addAll(template.getNodeProperties());

        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setName(name);
        podTemplate.setLabel(label);
        podTemplate.setNodeSelector(nodeSelector);
        podTemplate.setServiceAccount(serviceAccount);
        podTemplate.setEnvVars(combinedEnvVars.entrySet().stream().map(e -> new PodEnvVar(e.getKey(), e.getValue())).collect(Collectors.toList()));
        podTemplate.setContainers(new ArrayList<>(combinedContainers.values()));
        podTemplate.setVolumes(new ArrayList<>(combinedVolumes.values()));
        podTemplate.setImagePullSecrets(new ArrayList<>(imagePullSecrets));
        podTemplate.setNodeProperties(toolLocationNodeProperties);
        return podTemplate;
    }

    /**
     * Unwraps the hierarchy of the PodTemplate.
     * @param template
     * @return
     */
    static PodTemplate unwrap(PodTemplate template, Collection<PodTemplate> allTemplates) {
        if (template == null) {
            return null;
        }

        if (Strings.isNullOrEmpty(template.getInheritFrom())) {
            return template;
        } else {
            String[] parentLabels = template.getInheritFrom().split("[ ]+");
            PodTemplate parent = null;
            for (String label : parentLabels) {
                PodTemplate next = getTemplate(Label.get(label), allTemplates);
                if (next != null) {
                    parent = combine(parent, unwrap(next, allTemplates));
                }
            }
            return combine(parent, template);
        }
    }

    public static PodTemplate getTemplate(Label label, Collection<PodTemplate> templates) {
        for (PodTemplate t : templates) {
            if (label == null || label.matches(t.getLabelSet())) {
                return t;
            }
        }
        return null;
    }
}
