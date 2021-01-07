package org.csanchez.jenkins.plugins.kubernetes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.google.common.annotations.VisibleForTesting;
import hudson.slaves.NodeProperty;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.Util;
import hudson.model.Label;
import hudson.model.Node;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSource;
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
import io.fabric8.kubernetes.client.KubernetesClientException;

import static hudson.Util.replaceMacro;
import io.fabric8.kubernetes.client.utils.Serialization;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate.DEFAULT_WORKING_DIR;

public class PodTemplateUtils {

    private static final Logger LOGGER = Logger.getLogger(PodTemplateUtils.class.getName());

    private static final Pattern LABEL_VALIDATION = Pattern.compile("[a-zA-Z0-9]([_\\.\\-a-zA-Z0-9]*[a-zA-Z0-9])?");

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "tests & emergency admin")
    @VisibleForTesting
    public static boolean SUBSTITUTE_ENV = Boolean.getBoolean(PodTemplateUtils.class.getName() + ".SUBSTITUTE_ENV");

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
        String runAsUser = template.getRunAsUser() != null ? template.getRunAsUser() : parent.getRunAsUser();
        String runAsGroup = template.getRunAsGroup() != null ? template.getRunAsGroup() : parent.getRunAsGroup();
        boolean alwaysPullImage = template.isAlwaysPullImage() ? template.isAlwaysPullImage() : (parent.isAlwaysPullImage() ? parent.isAlwaysPullImage() : false);
        String workingDir = Strings.isNullOrEmpty(template.getWorkingDir()) ? (Strings.isNullOrEmpty(parent.getWorkingDir()) ? DEFAULT_WORKING_DIR : parent.getWorkingDir()) : template.getWorkingDir();
        String command = Strings.isNullOrEmpty(template.getCommand()) ? parent.getCommand() : template.getCommand();
        String args = Strings.isNullOrEmpty(template.getArgs()) ? parent.getArgs() : template.getArgs();
        boolean ttyEnabled = template.isTtyEnabled() ? template.isTtyEnabled() : (parent.isTtyEnabled() ? parent.isTtyEnabled() : false);
        String resourceRequestCpu = Strings.isNullOrEmpty(template.getResourceRequestCpu()) ? parent.getResourceRequestCpu() : template.getResourceRequestCpu();
        String resourceRequestMemory = Strings.isNullOrEmpty(template.getResourceRequestMemory()) ? parent.getResourceRequestMemory() : template.getResourceRequestMemory();
        String resourceRequestEphemeralStorage = Strings.isNullOrEmpty(template.getResourceRequestEphemeralStorage()) ? parent.getResourceRequestEphemeralStorage() : template.getResourceRequestEphemeralStorage();
        String resourceLimitCpu = Strings.isNullOrEmpty(template.getResourceLimitCpu()) ? parent.getResourceLimitCpu() : template.getResourceLimitCpu();
        String resourceLimitMemory = Strings.isNullOrEmpty(template.getResourceLimitMemory()) ? parent.getResourceLimitMemory() : template.getResourceLimitMemory();
        String resourceLimitEphemeralStorage = Strings.isNullOrEmpty(template.getResourceLimitEphemeralStorage()) ? parent.getResourceLimitEphemeralStorage() : template.getResourceLimitEphemeralStorage();
        Map<String, PortMapping> ports = parent.getPorts().stream()
                .collect(Collectors.toMap(PortMapping::getName, Function.identity()));
        template.getPorts().stream().forEach(p -> ports.put(p.getName(), p));

        ContainerTemplate combined = new ContainerTemplate(image);
        combined.setName(name);
        combined.setImage(image);
        combined.setAlwaysPullImage(alwaysPullImage);
        combined.setCommand(command);
        combined.setArgs(args);
        combined.setTtyEnabled(ttyEnabled);
        combined.setResourceLimitCpu(resourceLimitCpu);
        combined.setResourceLimitMemory(resourceLimitMemory);
        combined.setResourceLimitEphemeralStorage(resourceLimitEphemeralStorage);
        combined.setResourceRequestCpu(resourceRequestCpu);
        combined.setResourceRequestMemory(resourceRequestMemory);
        combined.setResourceRequestEphemeralStorage(resourceRequestEphemeralStorage);
        combined.setWorkingDir(workingDir);
        combined.setPrivileged(privileged);
        combined.setRunAsUser(runAsUser);
        combined.setRunAsGroup(runAsGroup);
        combined.setEnvVars(combineEnvVars(parent, template));
        combined.setPorts(new ArrayList<>(ports.values()));
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
        Long runAsUser = template.getSecurityContext() != null && template.getSecurityContext().getRunAsUser() != null
                ? template.getSecurityContext().getRunAsUser()
                : (parent.getSecurityContext() != null ? parent.getSecurityContext().getRunAsUser() : null);
        Long runAsGroup = template.getSecurityContext() != null && template.getSecurityContext().getRunAsGroup() != null
                ? template.getSecurityContext().getRunAsGroup()
                : (parent.getSecurityContext() != null ? parent.getSecurityContext().getRunAsGroup() : null);
        String imagePullPolicy = Strings.isNullOrEmpty(template.getImagePullPolicy()) ? parent.getImagePullPolicy()
                : template.getImagePullPolicy();
        String workingDir = Strings.isNullOrEmpty(template.getWorkingDir())
                ? (Strings.isNullOrEmpty(parent.getWorkingDir()) ? DEFAULT_WORKING_DIR : parent.getWorkingDir())
                : template.getWorkingDir();
        List<String> command = template.getCommand() == null ? parent.getCommand() : template.getCommand();
        List<String> args = template.getArgs() == null ? parent.getArgs() : template.getArgs();
        Boolean tty = template.getTty() != null ? template.getTty() : parent.getTty();
        Map<String, Quantity> requests = combineResources(parent, template, ResourceRequirements::getRequests);
        Map<String, Quantity> limits = combineResources(parent, template, ResourceRequirements::getLimits);

        Map<String, VolumeMount> volumeMounts = parent.getVolumeMounts().stream()
                .collect(Collectors.toMap(VolumeMount::getMountPath, Function.identity()));
        template.getVolumeMounts().stream().forEach(vm -> volumeMounts.put(vm.getMountPath(), vm));

        ContainerBuilder containerBuilder = new ContainerBuilder(parent) //
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
                .withEnvFrom(combinedEnvFromSources(parent, template))
                .withVolumeMounts(new ArrayList<>(volumeMounts.values()));
        if ((privileged != null && privileged) || runAsUser != null || runAsGroup != null) {
            containerBuilder = containerBuilder
                    .withNewSecurityContext()
                        .withPrivileged(privileged)
                        .withRunAsUser(runAsUser)
                        .withRunAsGroup(runAsGroup)
                    .endSecurityContext();
        }
        return containerBuilder.build();
    }

    private static Map<String, Quantity> combineResources(Container parent, Container template,
                                                          Function<ResourceRequirements,
                                                                   Map<String, Quantity>> resourceTypeMapper) {
        return Stream.of(template.getResources(), parent.getResources()) //
                .filter(Objects::nonNull) //
                .map(resourceTypeMapper) //
                .filter(Objects::nonNull) //
                .map(Map::entrySet) //
                .flatMap(Collection::stream) //
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1) // v2 (parent) loses
                );
    }

    /**
     * Combines all given pods together in order.
     * @param pods the pods to combine
     */
    public static Pod combine(List<Pod> pods) {
        Pod result = null;
        for (Pod p: pods) {
            if (result != null) {
                result = combine(result, p);
            } else {
                result = p;
            }
        }
        return result;
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

        LOGGER.finest(() -> "Combining pods, parent: " + Serialization.asYaml(parent) + " template: " + Serialization.asYaml(template));

        Map<String, String> nodeSelector = mergeMaps(parent.getSpec().getNodeSelector(),
                template.getSpec().getNodeSelector());
        String serviceAccount = Strings.isNullOrEmpty(template.getSpec().getServiceAccount())
                ? parent.getSpec().getServiceAccount()
                : template.getSpec().getServiceAccount();

        Boolean hostNetwork = template.getSpec().getHostNetwork() != null
                ? template.getSpec().getHostNetwork()
                : parent.getSpec().getHostNetwork();

        Map<String, String> podAnnotations = mergeMaps(parent.getMetadata().getAnnotations(),
                template.getMetadata().getAnnotations());
        Map<String, String> podLabels = mergeMaps(parent.getMetadata().getLabels(), template.getMetadata().getLabels());

        Set<LocalObjectReference> imagePullSecrets = new LinkedHashSet<>();
        imagePullSecrets.addAll(parent.getSpec().getImagePullSecrets());
        imagePullSecrets.addAll(template.getSpec().getImagePullSecrets());

        // Containers
        List<Container> combinedContainers = combineContainers(parent.getSpec().getContainers(), template.getSpec().getContainers());

        // Init containers
        List<Container> combinedInitContainers = combineContainers(parent.getSpec().getInitContainers(), template.getSpec().getInitContainers());

        // Volumes
        List<Volume> combinedVolumes = combineVolumes(parent.getSpec().getVolumes(), template.getSpec().getVolumes());

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
                .withHostNetwork(hostNetwork) //
                .withContainers(combinedContainers) //
                .withInitContainers(combinedInitContainers) //
                .withVolumes(combinedVolumes) //
                .withTolerations(combinedTolerations) //
                .withImagePullSecrets(Lists.newArrayList(imagePullSecrets));


        // Security context
        if (template.getSpec().getSecurityContext() != null || parent.getSpec().getSecurityContext() != null) {
            specBuilder.editOrNewSecurityContext()
                    .withRunAsUser(
                            template.getSpec().getSecurityContext() != null && template.getSpec().getSecurityContext().getRunAsUser() != null ? template.getSpec().getSecurityContext().getRunAsUser() : (
                                    parent.getSpec().getSecurityContext() != null && parent.getSpec().getSecurityContext().getRunAsUser() != null ? parent.getSpec().getSecurityContext().getRunAsUser() : null
                            )
                    )
                    .withRunAsGroup(
                            template.getSpec().getSecurityContext() != null && template.getSpec().getSecurityContext().getRunAsGroup() != null ? template.getSpec().getSecurityContext().getRunAsGroup() : (
                                    parent.getSpec().getSecurityContext() != null && parent.getSpec().getSecurityContext().getRunAsGroup() != null ? parent.getSpec().getSecurityContext().getRunAsGroup() : null
                            )
                    )
                    .endSecurityContext();
        }

        // podTemplate.setLabel(label);
//        podTemplate.setEnvVars(combineEnvVars(parent, template));
//        podTemplate.setWorkspaceVolume(workspaceVolume);
//        podTemplate.setNodeProperties(toolLocationNodeProperties);
//        podTemplate.setNodeUsageMode(nodeUsageMode);
//        podTemplate.setYaml(template.getYaml() == null ? parent.getYaml() : template.getYaml());

        Pod pod = specBuilder.endSpec().build();
        LOGGER.finest(() -> "Pods combined: " + Serialization.asYaml(pod));
        return pod;
    }

    @Nonnull
    private static List<Container> combineContainers(List<Container> parent, List<Container> child) {
        List<Container> combinedContainers = new ArrayList<>();
        Map<String, Container> parentContainers = parent.stream()
                .collect(toMap(c -> c.getName(), c -> c));
        Map<String, Container> childContainers = child.stream()
                .collect(toMap(c -> c.getName(), c -> combine(parentContainers.get(c.getName()), c)));
        combinedContainers.addAll(parentContainers.values());
        combinedContainers.addAll(childContainers.values());
        return combinedContainers;
    }

    private static List<Volume> combineVolumes(@Nonnull List<Volume> volumes1, @Nonnull List<Volume> volumes2) {
        Map<String, Volume> volumesByName = volumes1.stream().collect(Collectors.toMap(Volume::getName, Function.identity()));
        volumes2.forEach(v -> volumesByName.put(v.getName(), v));
        return new ArrayList<>(volumesByName.values());
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

        WorkspaceVolume workspaceVolume = WorkspaceVolume.merge(parent.getWorkspaceVolume(), template.getWorkspaceVolume());

        //Tool location node properties
        List<NodeProperty<?>> nodeProperties = new ArrayList<>(parent.getNodeProperties());
        nodeProperties.addAll(template.getNodeProperties());

        PodTemplate podTemplate = new PodTemplate(template.getId());
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
        podTemplate.setNodeProperties(nodeProperties);
        podTemplate.setNodeUsageMode(nodeUsageMode);
        podTemplate.setYamlMergeStrategy(template.getYamlMergeStrategy());
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

        podTemplate.setPodRetention(template.getPodRetention());
        podTemplate.setShowRawYaml(template.isShowRawYamlSet() ? template.isShowRawYaml() : parent.isShowRawYaml());

        podTemplate.setRunAsUser(template.getRunAsUser() != null ? template.getRunAsUser() : parent.getRunAsUser());
        podTemplate.setRunAsGroup(template.getRunAsGroup() != null ? template.getRunAsGroup() : parent.getRunAsGroup());

        podTemplate.setSupplementalGroups(template.getSupplementalGroups() != null ? template.getSupplementalGroups() : parent.getSupplementalGroups());

        if (template.isHostNetworkSet()) {
            podTemplate.setHostNetwork(template.isHostNetwork());
        } else if (parent.isHostNetworkSet()) {
            podTemplate.setHostNetwork(parent.isHostNetwork());
        }

        List<String> yamls = new ArrayList<>(parent.getYamls());
        yamls.addAll(template.getYamls());
        podTemplate.setYamls(yamls);

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
     * @deprecated Potentially insecure; a no-op by default.
     */
    @Deprecated
    public static String substituteEnv(String s) {
        return SUBSTITUTE_ENV ? replaceMacro(s, System.getenv()) : s;
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
        String s = yaml;
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            // JENKINS-57116
            if (StringUtils.isBlank(s)) {
                LOGGER.log(Level.WARNING, "[JENKINS-57116] Trying to parse invalid yaml: \"{0}\"", yaml);
                s = "{}";
            }
            Pod podFromYaml;
            try (InputStream is = new ByteArrayInputStream(s.getBytes(UTF_8))) {
                podFromYaml = client.pods().load(is).get();
            } catch (IOException | KubernetesClientException e) {
                throw new RuntimeException(String.format("Failed to parse yaml: \"%s\"", yaml), e);
            }
            LOGGER.finest(() -> "Parsed pod template from yaml: " + Serialization.asYaml(podFromYaml));
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

    public static Collection<String> validateYamlContainerNames(List<String> yamls) {
        Collection<String> errors = new ArrayList<>();
        for (String yaml : yamls) {
            errors.addAll(validateYamlContainerNames(yaml));
        }
        return errors;
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

    /** TODO perhaps enforce https://docs.docker.com/engine/reference/commandline/tag/#extended-description */
    public static boolean validateImage(String image) {
        return image != null && image.matches("\\S+");
    }

    private static List<EnvVar> combineEnvVars(Container parent, Container template) {
        Map<String,EnvVar> combinedEnvVars = mergeMaps(envVarstoMap(parent.getEnv()),envVarstoMap(template.getEnv()));
        return combinedEnvVars.entrySet().stream()
                .filter(envVar -> !Strings.isNullOrEmpty(envVar.getKey()))
                .map(Map.Entry::getValue)
                .collect(toList());
    }

    @VisibleForTesting
    static Map<String, EnvVar> envVarstoMap(List<EnvVar> envVarList) {
        return envVarList.stream().collect(toMap(EnvVar::getName, Function.identity()));
    }

    private static List<TemplateEnvVar> combineEnvVars(ContainerTemplate parent, ContainerTemplate template) {
        return combineEnvVars(parent.getEnvVars(), template.getEnvVars());
    }

    private static List<TemplateEnvVar> combineEnvVars(PodTemplate parent, PodTemplate template) {
        return combineEnvVars(parent.getEnvVars(), template.getEnvVars());
    }

    private static List<TemplateEnvVar> combineEnvVars(List<TemplateEnvVar> parent, List<TemplateEnvVar> child) {
        Map<String,TemplateEnvVar> combinedEnvVars = mergeMaps(templateEnvVarstoMap(parent),templateEnvVarstoMap(child));
        return combinedEnvVars
                .entrySet()
                .stream()
                .filter(entry -> !Strings.isNullOrEmpty(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(toList());
    }

    @VisibleForTesting
    static Map<String, TemplateEnvVar> templateEnvVarstoMap(List<TemplateEnvVar> envVarList) {
        return envVarList
                .stream()
                .collect(Collectors.toMap(TemplateEnvVar::getKey, Function.identity(), throwingMerger(), LinkedHashMap::new));
    }

    private static <T> BinaryOperator<T> throwingMerger() {
        return (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); };
    }

    private static List<EnvFromSource> combinedEnvFromSources(Container parent, Container template) {
        List<EnvFromSource> combinedEnvFromSources = new ArrayList<>();
        combinedEnvFromSources.addAll(parent.getEnvFrom());
        combinedEnvFromSources.addAll(template.getEnvFrom());
        return combinedEnvFromSources.stream().filter(envFromSource ->
                envFromSource.getConfigMapRef() != null && !Strings.isNullOrEmpty(envFromSource.getConfigMapRef().getName()) ||
                        envFromSource.getSecretRef() != null && !Strings.isNullOrEmpty(envFromSource.getSecretRef().getName())
        ).collect(toList());
    }

    private static <K, V> Map<K, V> mergeMaps(Map<K, V> m1, Map<K, V> m2) {
        Map<K, V> m = new LinkedHashMap<>();
        if (m1 != null)
            m.putAll(m1);
        if (m2 != null)
            m.putAll(m2);
        return m;
    }

    static Long parseLong(String value) {
        String s = Util.fixEmptyAndTrim(value);
        if (s != null) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
    }
}
