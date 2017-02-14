package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.Label;
import hudson.tools.ToolLocationNodeProperty;

import static org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate.DEFAULT_WORKING_DIR;

public class PodTemplateUtils {

    private static final String PLACEHOLDER_KEY = "key";
    private static final String PLACEHOLDER_FORMAT = "\\$\\{%s\\}";
    private static final String PLACEHOLDER_REGEX = String.format(PLACEHOLDER_FORMAT, "(?<" + PLACEHOLDER_KEY + ">[a-zA-Z0-9_]+)");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(PLACEHOLDER_REGEX);

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
        String workingDir = Strings.isNullOrEmpty(template.getWorkingDir()) ? (Strings.isNullOrEmpty(parent.getWorkingDir()) ? DEFAULT_WORKING_DIR : parent.getWorkingDir()) : template.getCommand();
        String command = Strings.isNullOrEmpty(template.getCommand()) ? parent.getCommand() : template.getCommand();
        String args = Strings.isNullOrEmpty(template.getArgs()) ? parent.getArgs() : template.getArgs();
        boolean ttyEnabled = template.isTtyEnabled() ? template.isTtyEnabled() : (parent.isTtyEnabled() ? parent.isTtyEnabled() : false);;
        String resourceRequestCpu = Strings.isNullOrEmpty(template.getResourceRequestCpu()) ? parent.getResourceRequestCpu() : template.getResourceRequestCpu();
        String resourceRequestMemory = Strings.isNullOrEmpty(template.getResourceRequestMemory()) ? parent.getResourceRequestMemory() : template.getResourceRequestMemory();
        String resourceLimitCpu = Strings.isNullOrEmpty(template.getResourceLimitCpu()) ? parent.getResourceLimitCpu() : template.getResourceLimitCpu();
        String resourceLimitMemory = Strings.isNullOrEmpty(template.getResourceLimitMemory()) ? parent.getResourceLimitMemory() : template.getResourceLimitMemory();

        List<ContainerEnvVar> combinedEnvVars = new ArrayList<ContainerEnvVar>();
        Map<String, String> envVars = new HashMap<>();
        parent.getEnvVars().stream().filter(e -> !Strings.isNullOrEmpty(e.getKey())).forEach(
                e -> envVars.put(e.getKey(), e.getValue())
        );

        template.getEnvVars().stream().filter(e -> !Strings.isNullOrEmpty(e.getKey())).forEach(
                e -> envVars.put(e.getKey(), e.getValue())
        );

        envVars.entrySet().forEach(e -> combinedEnvVars.add(new ContainerEnvVar(e.getKey(), e.getValue())));

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

        //Env Vars
        Map<String, String> combinedEnvVars = new HashMap<>();
        combinedEnvVars.putAll(parent.getEnvVars().stream().filter(e -> !Strings.isNullOrEmpty(e.getKey())).collect(Collectors.toMap(e -> e.getKey(),e -> e.getValue())));
        combinedEnvVars.putAll(template.getEnvVars().stream().filter(e -> !Strings.isNullOrEmpty(e.getKey())).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue())));

        //Containers
        Map<String, ContainerTemplate> parentContainers = parent.getContainers().stream().collect(Collectors.toMap(c -> c.getName(), c -> c));
        combinedContainers.putAll(parentContainers);
        combinedContainers.putAll(template.getContainers().stream().collect(Collectors.toMap(c -> c.getName(), c -> combine(parentContainers.get(c.getName()), c))));

        //Volumes
        Map<String, PodVolume> parentVolumes = parent.getVolumes().stream().collect(Collectors.toMap(v -> v.getMountPath(), v -> v));
        combinedVolumes.putAll(parentVolumes);
        combinedVolumes.putAll(template.getVolumes().stream().collect(Collectors.toMap(v -> v.getMountPath(), v -> v)));

        WorkspaceVolume workspaceVolume = template.isCustomWorkspaceVolumeEnabled() && template.getWorkspaceVolume() != null ? template.getWorkspaceVolume() : parent.getWorkspaceVolume();

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
        podTemplate.setWorkspaceVolume(workspaceVolume);
        podTemplate.setVolumes(new ArrayList<>(combinedVolumes.values()));
        podTemplate.setImagePullSecrets(new ArrayList<>(imagePullSecrets));
        podTemplate.setNodeProperties(toolLocationNodeProperties);
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
            if (label == null || label.matches(t.getLabelSet())) {
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
        return substitute(s, System.getenv());
    }

    /**
     * Substitutes a placeholder with a value found in the environment.
     * @param s             The placeholder. Should be use the format: ${placeholder}.
     * @param defaultValue  The default value to return if no match is found.
     * @return              The substituted value if found, or the default value otherwise.
     */
    public static String substituteEnv(String s, String defaultValue) {
        return substitute(s, System.getenv(), defaultValue);
    }

    /**
     * Substitutes a placeholder with a value found in the specified map.
     * @param s             The placeholder. Should be use the format: ${placeholder}.
     * @param properties    The map with the key value pairs to use for substitution.
     * @return              The substituted value if found, or the input value otherwise.
     */
    public static String substitute(String s, Map<String, String> properties) {
        return substitute(s, properties, null);
    }

    /**
     * Substitutes a placeholder with a value found in the specified map.
     * @param s             The placeholder. Should be use the format: ${placeholder}.
     * @param properties    The map with the key value pairs to use for substitution.
     * @param defaultValue  The default value to return if no match is found.
     * @return              The substituted value if found, or the default value otherwise.
     */
    public static String substitute(String s, Map<String, String> properties, String defaultValue) {
        if (Strings.isNullOrEmpty(s)) {
            return defaultValue;
        }

        Matcher m = PLACEHOLDER_PATTERN.matcher(s);
        while (m.find()) {
            String key = m.group(PLACEHOLDER_KEY);
            String val = properties.get(key);
            if (val != null) {
                s = s.replaceAll(String.format(PLACEHOLDER_FORMAT, key), val);
            } else if (defaultValue != null) {
                s = s.replaceAll(String.format(PLACEHOLDER_FORMAT, key), defaultValue);
            }
        }
        return s;
    }
}
