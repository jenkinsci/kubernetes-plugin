package org.csanchez.jenkins.plugins.kubernetes;

import static hudson.Util.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import hudson.Util;
import hudson.model.Label;
import hudson.model.Node;
import hudson.tools.ToolLocationNodeProperty;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent.MetadataNested;
import io.fabric8.kubernetes.api.model.PodFluent.SpecNested;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

public class PodTemplateUtils {

    private static final Logger LOGGER = Logger.getLogger(PodTemplateUtils.class.getName());

    private static final Pattern LABEL_VALIDATION = Pattern.compile("[a-zA-Z0-9]([_\\.\\-a-zA-Z0-9]*[a-zA-Z0-9])?");

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
     * Combines a Container with its parent.
     *
     * @param parent
     *            The parent container (nullable).
     * @param template
     *            The actual container
     * @return The combined container.
     */
    public static Container combine(@CheckForNull Container parent, @Nonnull Container template) {
        Preconditions.checkNotNull(template, "Container template should not be null");
        if (parent == null) {
            return template;
        }

        String name = template.getName();
        String image = Strings.isNullOrEmpty(template.getImage()) ? parent.getImage() : template.getImage();
        Boolean privileged = template.getSecurityContext() != null && template.getSecurityContext().getPrivileged() != null
                ? template.getSecurityContext().getPrivileged()
                : (parent.getSecurityContext() != null ? parent.getSecurityContext().getPrivileged() : Boolean.FALSE);
        String imagePullPolicy = Strings.isNullOrEmpty(template.getImagePullPolicy()) ? parent.getImagePullPolicy()
                : template.getImagePullPolicy();
        String workingDir = Strings.isNullOrEmpty(template.getWorkingDir())
                ? (Strings.isNullOrEmpty(parent.getWorkingDir()) ? DEFAULT_WORKING_DIR : parent.getWorkingDir())
                : template.getWorkingDir();
        List<String> command = template.getCommand() == null ? parent.getCommand() : template.getCommand();
        List<String> args = template.getArgs() == null ? parent.getArgs() : template.getArgs();
        Boolean tty = template.getTty() != null ? template.getTty() : parent.getTty();
        Map<String, Quantity> requests = new HashMap<>();
        safeGet(parent, template, ResourceRequirements::getRequests, "cpu", requests);
        safeGet(parent, template, ResourceRequirements::getRequests, "memory", requests);
        Map<String, Quantity> limits = new HashMap<>();
        safeGet(parent, template, ResourceRequirements::getLimits, "cpu", limits);
        safeGet(parent, template, ResourceRequirements::getLimits, "memory", limits);
        
        Map<String, VolumeMount> volumeMounts = parent.getVolumeMounts().stream()
                .collect(Collectors.toMap(VolumeMount::getMountPath, Function.identity()));
        template.getVolumeMounts().stream().forEach(vm -> volumeMounts.put(vm.getMountPath(), vm));

        Container combined = new ContainerBuilder(parent) //
                .withImage(image) //
                .withName(name) //
                .withImagePullPolicy(imagePullPolicy) //
                .withCommand(command) //
                .withWorkingDir(workingDir) //
                .withArgs(args) //
                .withTty(tty) //
                .withNewResources() //
                .withRequests(ImmutableMap.copyOf(requests)) //
                .withLimits(ImmutableMap.copyOf(limits)) //
                .endResources() //
                .withEnv(combineEnvVars(parent, template)) //
                .withNewSecurityContext().withPrivileged(privileged).endSecurityContext() //
                .withVolumeMounts(new ArrayList<>(volumeMounts.values())) //
                .build();

        return combined;
    }

    private static void safeGet(Container parent, Container template,
                                Function<ResourceRequirements, Map<String, Quantity>> resourceTypeMapper,
                                String field, Map<String, Quantity> out) {
        Quantity data;
        data = Optional.ofNullable(template.getResources())
                .map(resourceTypeMapper)
                .map(rT -> rT.get(field))
                .filter(tF -> !Strings.isNullOrEmpty(tF.getAmount()))
                .orElse(Optional.ofNullable(parent.getResources())
                        .map(resourceTypeMapper)
                        .map(rT -> rT.get(field))
                        .orElse(null)
                );
        if (data != null) {
            out.put(field, data);
        }   
    }

    /**
     * Combines a Pod with its parent.
     * @param parent        The parent Pod (nullable).
     * @param template      The child Pod
     */
    public static Pod combine(Pod parent, Pod template) {
        Preconditions.checkNotNull(template, "Pod template should not be null");
        if (parent == null) {
            return template;
        }

        LOGGER.log(Level.FINE, "Combining pods, parent: {0}", parent);
        LOGGER.log(Level.FINE, "Combining pods, template: {0}", template);

        Map<String, String> nodeSelector = mergeMaps(parent.getSpec().getNodeSelector(),
                template.getSpec().getNodeSelector());
        String serviceAccount = Strings.isNullOrEmpty(template.getSpec().getServiceAccount())
                ? parent.getSpec().getServiceAccount()
                : template.getSpec().getServiceAccount();

        Map<String, String> podAnnotations = mergeMaps(parent.getMetadata().getAnnotations(),
                template.getMetadata().getAnnotations());
        Map<String, String> podLabels = mergeMaps(parent.getMetadata().getLabels(), template.getMetadata().getLabels());

        Set<LocalObjectReference> imagePullSecrets = new LinkedHashSet<>();
        imagePullSecrets.addAll(parent.getSpec().getImagePullSecrets());
        imagePullSecrets.addAll(template.getSpec().getImagePullSecrets());

        // Containers
        Map<String, Container> combinedContainers = new HashMap<>();
        Map<String, Container> parentContainers = parent.getSpec().getContainers().stream()
                .collect(toMap(c -> c.getName(), c -> c));
        combinedContainers.putAll(parentContainers);
        combinedContainers.putAll(template.getSpec().getContainers().stream()
                .collect(toMap(c -> c.getName(), c -> combine(parentContainers.get(c.getName()), c))));

        // Volumes
        List<Volume> combinedVolumes = Lists.newLinkedList();
        Optional.ofNullable(parent.getSpec().getVolumes()).ifPresent(combinedVolumes::addAll);
        Optional.ofNullable(template.getSpec().getVolumes()).ifPresent(combinedVolumes::addAll);

        // Tolerations
        List<Toleration> combinedTolerations = Lists.newLinkedList();
        Optional.ofNullable(parent.getSpec().getTolerations()).ifPresent(combinedTolerations::addAll);
        Optional.ofNullable(template.getSpec().getTolerations()).ifPresent(combinedTolerations::addAll);

//        WorkspaceVolume workspaceVolume = template.isCustomWorkspaceVolumeEnabled() && template.getWorkspaceVolume() != null ? template.getWorkspaceVolume() : parent.getWorkspaceVolume();

        //Tool location node properties
//        List<ToolLocationNodeProperty> toolLocationNodeProperties = new ArrayList<>();
//        toolLocationNodeProperties.addAll(parent.getNodeProperties());
//        toolLocationNodeProperties.addAll(template.getNodeProperties());

        MetadataNested<PodBuilder> metadataBuilder = new PodBuilder(parent).withNewMetadataLike(parent.getMetadata()) //
                .withAnnotations(podAnnotations).withLabels(podLabels);
        if (!Strings.isNullOrEmpty(template.getMetadata().getName())) {
            metadataBuilder.withName(template.getMetadata().getName());
        }
        if (!Strings.isNullOrEmpty(template.getMetadata().getNamespace())) {
            metadataBuilder.withNamespace(template.getMetadata().getNamespace());
        }

        SpecNested<PodBuilder> specBuilder = metadataBuilder.endMetadata() //
                .withNewSpecLike(parent.getSpec()) //
                .withNodeSelector(nodeSelector) //
                .withServiceAccount(serviceAccount) //
                .withContainers(Lists.newArrayList(combinedContainers.values())) //
                .withVolumes(combinedVolumes) //
                .withTolerations(combinedTolerations) //
                .withImagePullSecrets(Lists.newArrayList(imagePullSecrets));

        // podTemplate.setLabel(label);
//        podTemplate.setEnvVars(combineEnvVars(parent, template));
//        podTemplate.setWorkspaceVolume(workspaceVolume);
//        podTemplate.setNodeProperties(toolLocationNodeProperties);
//        podTemplate.setNodeUsageMode(nodeUsageMode);
//        podTemplate.setYaml(template.getYaml() == null ? parent.getYaml() : template.getYaml());

        Pod pod = specBuilder.endSpec().build();
        LOGGER.log(Level.FINE, "Pods combined: {0}", pod);
        return pod;
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

        LOGGER.log(Level.FINEST, "Combining pod templates, parent: {0}", parent);
        LOGGER.log(Level.FINEST, "Combining pod templates, template: {0}", template);

        String name = template.getName();
        String label = template.getLabel();
        String nodeSelector = Strings.isNullOrEmpty(template.getNodeSelector()) ? parent.getNodeSelector() : template.getNodeSelector();
        String serviceAccount = Strings.isNullOrEmpty(template.getServiceAccount()) ? parent.getServiceAccount() : template.getServiceAccount();
        Node.Mode nodeUsageMode = template.getNodeUsageMode() == null ? parent.getNodeUsageMode() : template.getNodeUsageMode();

        Set<PodAnnotation> podAnnotations = new LinkedHashSet<>();
        podAnnotations.addAll(template.getAnnotations());
        podAnnotations.addAll(parent.getAnnotations());

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
        podTemplate.setInheritFrom(!Strings.isNullOrEmpty(template.getInheritFrom()) ?
                                   template.getInheritFrom() : parent.getInheritFrom());

        podTemplate.setInstanceCap(template.getInstanceCap() != Integer.MAX_VALUE ?
                                   template.getInstanceCap() : parent.getInstanceCap());

        podTemplate.setSlaveConnectTimeout(template.getSlaveConnectTimeout() != PodTemplate.DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT ?
                                           template.getSlaveConnectTimeout() : parent.getSlaveConnectTimeout());

        podTemplate.setIdleMinutes(template.getIdleMinutes() != 0 ?
                                   template.getIdleMinutes() : parent.getIdleMinutes());

        podTemplate.setActiveDeadlineSeconds(template.getActiveDeadlineSeconds() != 0 ?
                                             template.getActiveDeadlineSeconds() : parent.getActiveDeadlineSeconds());


        podTemplate.setServiceAccount(!Strings.isNullOrEmpty(template.getServiceAccount()) ?
                                      template.getServiceAccount() : parent.getServiceAccount());

        podTemplate.setCustomWorkspaceVolumeEnabled(template.isCustomWorkspaceVolumeEnabled() ?
                                                    template.isCustomWorkspaceVolumeEnabled() : parent.isCustomWorkspaceVolumeEnabled());

        podTemplate.setYaml(template.getYaml() == null ? parent.getYaml() : template.getYaml());

        LOGGER.log(Level.FINEST, "Pod templates combined: {0}", podTemplate);
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
            PodTemplate combined = combine(parent, template);
            LOGGER.log(Level.FINEST, "Combined parent + template is {0}", combined);
            return combined;
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

    public static Pod parseFromYaml(String yaml) {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            Pod podFromYaml = client.pods().load(new ByteArrayInputStream((yaml == null ? "" : yaml).getBytes(UTF_8)))
                    .get();
            LOGGER.log(Level.FINEST, "Parsed pod template from yaml: {0}", podFromYaml);
            // yaml can be just a fragment, avoid NPEs
            if (podFromYaml.getMetadata() == null) {
                podFromYaml.setMetadata(new ObjectMeta());
            }
            if (podFromYaml.getSpec() == null) {
                podFromYaml.setSpec(new PodSpec());
            }
            return podFromYaml;
        }
    }

    public static Collection<String> validateYamlContainerNames(String yaml) {
        if (StringUtils.isBlank(yaml)) {
            return Collections.emptyList();
        }
        Collection<String> errors = new ArrayList<>();
        Pod pod = parseFromYaml(yaml);
        List<Container> containers = pod.getSpec().getContainers();
        if (containers != null) {
            for (Container container : containers) {
                if (!PodTemplateUtils.validateContainerName(container.getName())) {
                    errors.add(container.getName());
                }
            }
        }
        return errors;
    }

    public static boolean validateContainerName(String name) {
        if (name != null && !name.isEmpty()) {
            Pattern p = Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
            Matcher m = p.matcher(name);
            return m.matches();
        }
        return true;
    }

    /*
     * Pulled from https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#syntax-and-character-set
     */
    public static boolean validateLabel(String label) {
        return StringUtils.isBlank(label) ? true : label.length() <= 63 && LABEL_VALIDATION.matcher(label).matches();
    }

    private static List<EnvVar> combineEnvVars(Container parent, Container template) {
        List<EnvVar> combinedEnvVars = new ArrayList<>();
        combinedEnvVars.addAll(parent.getEnv());
        combinedEnvVars.addAll(template.getEnv());
        return combinedEnvVars.stream().filter(envVar -> !Strings.isNullOrEmpty(envVar.getName())).collect(toList());
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

    private static <K, V> Map<K, V> mergeMaps(Map<K, V> m1, Map<K, V> m2) {
        Map<K, V> m = new HashMap<>();
        if (m1 != null)
            m.putAll(m1);
        if (m2 != null)
            m.putAll(m2);
        return m;
    }
}
