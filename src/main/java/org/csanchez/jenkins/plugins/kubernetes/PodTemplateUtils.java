package org.csanchez.jenkins.plugins.kubernetes;

import static hudson.Util.*;
import static java.util.stream.Collectors.*;
import static org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;


import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import hudson.Util;
import hudson.model.Label;
import hudson.model.Node;
import hudson.tools.ToolLocationNodeProperty;

public class PodTemplateUtils {

    /**
     * Combines a {@link ContainerTemplate} with its parent.
     * @param parent        The parent container template (nullable).
     * @param template      The actual container template
     * @return              The combined container template.
     */
    public static ContainerTemplate combine(@CheckForNull ContainerTemplate parent, @Nonnull ContainerTemplate template) {
        Preconditions.checkNotNull(template, "Container template should not be null");
        if (parent == null) {
            return template;
        }

        String name = template.getName();
        String image = Strings.isNullOrEmpty(template.getImage()) ? parent.getImage() : template.getImage();
        boolean privileged = template.isPrivileged() ? template.isPrivileged() : (parent.isPrivileged() ? parent.isPrivileged() : false);
        boolean alwaysPullImage = template.isAlwaysPullImage() ? template.isAlwaysPullImage() : (parent.isAlwaysPullImage() ? parent.isAlwaysPullImage() : false);
        String workingDir = Strings.isNullOrEmpty(template.getWorkingDir()) ? (Strings.isNullOrEmpty(parent.getWorkingDir()) ? DEFAULT_WORKING_DIR : parent.getWorkingDir()) : template.getWorkingDir();
        String command = Strings.isNullOrEmpty(template.getCommand()) ? parent.getCommand() : template.getCommand();
        String args = Strings.isNullOrEmpty(template.getArgs()) ? parent.getArgs() : template.getArgs();
        boolean ttyEnabled = template.isTtyEnabled() ? template.isTtyEnabled() : (parent.isTtyEnabled() ? parent.isTtyEnabled() : false);
        String resourceRequestCpu = Strings.isNullOrEmpty(template.getResourceRequestCpu()) ? parent.getResourceRequestCpu() : template.getResourceRequestCpu();
        String resourceRequestMemory = Strings.isNullOrEmpty(template.getResourceRequestMemory()) ? parent.getResourceRequestMemory() : template.getResourceRequestMemory();
        String resourceLimitCpu = Strings.isNullOrEmpty(template.getResourceLimitCpu()) ? parent.getResourceLimitCpu() : template.getResourceLimitCpu();
        String resourceLimitMemory = Strings.isNullOrEmpty(template.getResourceLimitMemory()) ? parent.getResourceLimitMemory() : template.getResourceLimitMemory();

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
        combined.setEnvVars(combineEnvVars(parent, template));
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
        Node.Mode nodeUsageMode = template.getNodeUsageMode() == null ? parent.getNodeUsageMode() : template.getNodeUsageMode();
        
        Set<PodAnnotation> podAnnotations = new LinkedHashSet<>();
        podAnnotations.addAll(parent.getAnnotations());
        podAnnotations.addAll(template.getAnnotations());
        
        Set<PodImagePullSecret> imagePullSecrets = new LinkedHashSet<>();
        imagePullSecrets.addAll(parent.getImagePullSecrets());
        imagePullSecrets.addAll(template.getImagePullSecrets());

        Map<String, ContainerTemplate> combinedContainers = new HashMap<>();
        Map<String, PodVolume> combinedVolumes = new HashMap<>();

        //Containers
        Map<String, ContainerTemplate> parentContainers = parent.getContainers().stream().collect(toMap(c -> c.getName(), c -> c));
        combinedContainers.putAll(parentContainers);
        combinedContainers.putAll(template.getContainers().stream().collect(toMap(c -> c.getName(), c -> combine(parentContainers.get(c.getName()), c))));

        //Volumes
        Map<String, PodVolume> parentVolumes = parent.getVolumes().stream().collect(toMap(v -> v.getMountPath(), v -> v));
        combinedVolumes.putAll(parentVolumes);
        combinedVolumes.putAll(template.getVolumes().stream().collect(toMap(v -> v.getMountPath(), v -> v)));

        WorkspaceVolume workspaceVolume = template.isCustomWorkspaceVolumeEnabled() && template.getWorkspaceVolume() != null ? template.getWorkspaceVolume() : parent.getWorkspaceVolume();

        //Tool location node properties
        List<ToolLocationNodeProperty> toolLocationNodeProperties = new ArrayList<>();
        toolLocationNodeProperties.addAll(parent.getNodeProperties());
        toolLocationNodeProperties.addAll(template.getNodeProperties());

        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setName(name);
        podTemplate.setNamespace(!Strings.isNullOrEmpty(template.getNamespace()) ? template.getNamespace() : parent.getNamespace());
        podTemplate.setLabel(label);
        podTemplate.setNodeSelector(nodeSelector);
        podTemplate.setServiceAccount(serviceAccount);
        podTemplate.setEnvVars(combineEnvVars(parent, template));
        podTemplate.setContainers(new ArrayList<>(combinedContainers.values()));
        podTemplate.setWorkspaceVolume(workspaceVolume);
        podTemplate.setVolumes(new ArrayList<>(combinedVolumes.values()));
        podTemplate.setImagePullSecrets(new ArrayList<>(imagePullSecrets));
        podTemplate.setAnnotations(new ArrayList<>(podAnnotations));
        podTemplate.setNodeProperties(toolLocationNodeProperties);
        podTemplate.setNodeUsageMode(nodeUsageMode);

        return podTemplate;
    }

    /**
     * Unwraps the hierarchy of the PodTemplate.
     *
     * @param template                   The template to unwrap.
     * @param defaultProviderTemplate    The name of the template that provides the default values.
     * @param allTemplates               A collection of all the known templates
     * @return
     */
    static PodTemplate unwrap(PodTemplate template, String defaultProviderTemplate, Collection<PodTemplate> allTemplates) {
        if (template == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (!Strings.isNullOrEmpty(defaultProviderTemplate)) {
            sb.append(defaultProviderTemplate).append(" ");

        }
        if (!Strings.isNullOrEmpty(template.getInheritFrom())) {
            sb.append(template.getInheritFrom()).append(" ");
        }
        String inheritFrom = sb.toString();

        if (Strings.isNullOrEmpty(inheritFrom)) {
            return template;
        } else {
            String[] parentNames = inheritFrom.split("[ ]+");
            PodTemplate parent = null;
            for (String name : parentNames) {
                PodTemplate next = getTemplateByName(name, allTemplates);
                if (next != null) {
                    parent = combine(parent, unwrap(next, allTemplates));
                }
            }
            return combine(parent, template);
        }
    }

    /**
     * Unwraps the hierarchy of the PodTemplate.
     *
     * @param template                The template to unwrap.
     * @param allTemplates            A collection of all the known templates
     * @return
     */
    static PodTemplate unwrap(PodTemplate template, Collection<PodTemplate> allTemplates) {
        return unwrap(template, null, allTemplates);
    }


    /**
     * Gets the {@link PodTemplate} by {@link Label}.
     * @param label         The label.
     * @param templates     The list of all templates.
     * @return              The first pod template from the collection that has a matching label.
     */
    public static PodTemplate getTemplateByLabel(@CheckForNull Label label, Collection<PodTemplate> templates) {
        for (PodTemplate t : templates) {
            if ((label == null && t.getNodeUsageMode() == Node.Mode.NORMAL) || (label != null && label.matches(t.getLabelSet()))) {
                return t;
            }
        }
        return null;
    }

    /**
     * Gets the {@link PodTemplate} by name.
     * @param name          The name.
     * @param templates     The list of all templates.
     * @return              The first pod template from the collection that has a matching name.
     */
    public static PodTemplate getTemplateByName(@CheckForNull String name, Collection<PodTemplate> templates) {
        for (PodTemplate t : templates) {
            if (name != null && name.equals(t.getName())) {
                return t;
            }
        }
        return null;
    }

    /**
     * Substitutes a placeholder with a value found in the environment.
     * @param s     The placeholder. Should be use the format: ${placeholder}.
     * @return      The substituted value if found, or the input value otherwise.
     */
    public static String substituteEnv(String s) {
        return replaceMacro(s, System.getenv());
    }

    /**
     * Substitutes a placeholder with a value found in the environment.
     * @deprecated check if it is null or empty in the caller method, then use {@link #substituteEnv(String)}
     * @param s             The placeholder. Should be use the format: ${placeholder}.
     * @param defaultValue  The default value to return if no match is found.
     * @return              The substituted value if found, or the default value otherwise.
     */
    @Deprecated
    public static String substituteEnv(String s, String defaultValue) {
        return substitute(s, System.getenv(), defaultValue);
    }

    /**
     * Substitutes a placeholder with a value found in the specified map.
     * @deprecated use {@link Util#replaceMacro(String, Map)}
     * @param s             The placeholder. Should be use the format: ${placeholder}.
     * @param properties    The map with the key value pairs to use for substitution.
     * @return              The substituted value if found, or the input value otherwise.
     */
    @Deprecated
    public static String substitute(String s, Map<String, String> properties) {
        return replaceMacro(s, properties);
    }

    /**
     * Substitutes a placeholder with a value found in the specified map.
     * @deprecated check if it is null or empty in the caller method, then use {@link #substitute(String,Map)}
     * @param s             The placeholder. Should be use the format: ${placeholder}.
     * @param properties    The map with the key value pairs to use for substitution.
     * @param defaultValue  The default value to return if no match is found.
     * @return              The substituted value if found, or the default value otherwise.
     */
    @Deprecated
    public static String substitute(String s, Map<String, String> properties, String defaultValue) {
        return Strings.isNullOrEmpty(s) ? defaultValue : replaceMacro(s, properties);
    }

    private static List<TemplateEnvVar> combineEnvVars(ContainerTemplate parent, ContainerTemplate template) {
        List<TemplateEnvVar> combinedEnvVars = new ArrayList<>();
        combinedEnvVars.addAll(parent.getEnvVars());
        combinedEnvVars.addAll(template.getEnvVars());
        return combinedEnvVars.stream().filter(envVar -> !Strings.isNullOrEmpty(envVar.getKey())).collect(toList());
    }

    private static List<TemplateEnvVar> combineEnvVars(PodTemplate parent, PodTemplate template) {
        List<TemplateEnvVar> combinedEnvVars = new ArrayList<>();
        combinedEnvVars.addAll(parent.getEnvVars());
        combinedEnvVars.addAll(template.getEnvVars());
        return combinedEnvVars.stream().filter(envVar -> !Strings.isNullOrEmpty(envVar.getKey())).collect(toList());
    }
}
