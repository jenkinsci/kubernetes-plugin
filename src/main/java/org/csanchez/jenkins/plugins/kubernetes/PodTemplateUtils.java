package org.csanchez.jenkins.plugins.kubernetes;

import static hudson.Util.replaceMacro;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import io.fabric8.kubernetes.api.model.Capabilities;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;

public class PodTemplateUtils {

    private static final Logger LOGGER = Logger.getLogger(PodTemplateUtils.class.getName());

    private static final Pattern LABEL_VALIDATION = Pattern.compile("[a-zA-Z0-9]([_\\.\\-a-zA-Z0-9]*[a-zA-Z0-9])?");

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "tests & emergency admin")
    public static boolean SUBSTITUTE_ENV = Boolean.getBoolean(PodTemplateUtils.class.getName() + ".SUBSTITUTE_ENV");

    /**
     * Combines a {@link ContainerTemplate} with its parent.
     * @param parent        The parent container template (nullable).
     * @param template      The actual container template
     * @return              The combined container template.
     */
    public static ContainerTemplate combine(
            @CheckForNull ContainerTemplate parent, @NonNull ContainerTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Container template should not be null");
        }
        if (parent == null) {
            return template;
        }

        Map<String, PortMapping> ports =
                parent.getPorts().stream().collect(Collectors.toMap(PortMapping::getName, Function.identity()));
        template.getPorts().forEach(p -> ports.put(p.getName(), p));

        var h = new HierarchyResolver<>(parent, template);
        ContainerTemplate combined = new ContainerTemplate(
                template.getName(), h.resolve(ContainerTemplate::getImage, PodTemplateUtils::isNullOrEmpty));

        combined.setAlwaysPullImage(h.resolve(ContainerTemplate::isAlwaysPullImage, v -> !v));
        combined.setCommand(h.resolve(ContainerTemplate::getCommand, PodTemplateUtils::isNullOrEmpty));
        combined.setArgs(h.resolve(ContainerTemplate::getArgs, PodTemplateUtils::isNullOrEmpty));
        combined.setTtyEnabled(h.resolve(ContainerTemplate::isTtyEnabled, v -> !v));
        combined.setResourceLimitCpu(
                h.resolve(ContainerTemplate::getResourceLimitCpu, PodTemplateUtils::isNullOrEmpty));
        combined.setResourceLimitMemory(
                h.resolve(ContainerTemplate::getResourceLimitMemory, PodTemplateUtils::isNullOrEmpty));
        combined.setResourceLimitEphemeralStorage(
                h.resolve(ContainerTemplate::getResourceLimitEphemeralStorage, PodTemplateUtils::isNullOrEmpty));
        combined.setResourceRequestCpu(
                h.resolve(ContainerTemplate::getResourceRequestCpu, PodTemplateUtils::isNullOrEmpty));
        combined.setResourceRequestMemory(
                h.resolve(ContainerTemplate::getResourceRequestMemory, PodTemplateUtils::isNullOrEmpty));
        combined.setResourceRequestEphemeralStorage(
                h.resolve(ContainerTemplate::getResourceRequestEphemeralStorage, PodTemplateUtils::isNullOrEmpty));
        combined.setShell(h.resolve(ContainerTemplate::getShell, PodTemplateUtils::isNullOrEmpty));
        combined.setWorkingDir(h.resolve(ContainerTemplate::getWorkingDir, PodTemplateUtils::isNullOrEmpty));
        combined.setPrivileged(h.resolve(ContainerTemplate::isPrivileged, v -> !v));
        combined.setRunAsUser(h.resolve(ContainerTemplate::getRunAsUser, Objects::isNull));
        combined.setRunAsGroup(h.resolve(ContainerTemplate::getRunAsGroup, Objects::isNull));
        combined.setEnvVars(combineEnvVars(parent, template));
        combined.setPorts(new ArrayList<>(ports.values()));
        combined.setLivenessProbe(h.resolve(ContainerTemplate::getLivenessProbe, Objects::isNull));
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
    public static Container combine(@CheckForNull Container parent, @NonNull Container template) {
        if (template == null) {
            throw new IllegalArgumentException("Container template should not be null");
        }
        if (parent == null) {
            return template;
        }
        var h = new HierarchyResolver<>(parent, template);

        Boolean privileged;
        Long runAsUser;
        Long runAsGroup;
        if (template.getSecurityContext() != null) {
            if (parent.getSecurityContext() != null) {
                privileged = h.resolve(c -> c.getSecurityContext().getPrivileged(), Objects::isNull);
                runAsUser = h.resolve(c -> c.getSecurityContext().getRunAsUser(), Objects::isNull);
                runAsGroup = h.resolve(c -> c.getSecurityContext().getRunAsGroup(), Objects::isNull);
            } else {
                privileged = template.getSecurityContext().getPrivileged();
                runAsUser = template.getSecurityContext().getRunAsUser();
                runAsGroup = template.getSecurityContext().getRunAsGroup();
            }
        } else {
            if (parent.getSecurityContext() != null) {
                privileged = parent.getSecurityContext().getPrivileged();
                runAsUser = parent.getSecurityContext().getRunAsUser();
                runAsGroup = parent.getSecurityContext().getRunAsGroup();
            } else {
                privileged = Boolean.FALSE;
                runAsUser = null;
                runAsGroup = null;
            }
        }
        Map<String, VolumeMount> volumeMounts = parent.getVolumeMounts().stream()
                .collect(Collectors.toMap(VolumeMount::getMountPath, Function.identity()));
        template.getVolumeMounts().forEach(vm -> volumeMounts.put(vm.getMountPath(), vm));

        ContainerBuilder containerBuilder = new ContainerBuilder(parent) //
                .withImage(h.resolve(Container::getImage, PodTemplateUtils::isNullOrEmpty)) //
                .withName(template.getName()) //
                .withImagePullPolicy(h.resolve(Container::getImagePullPolicy, PodTemplateUtils::isNullOrEmpty)) //
                .withCommand(h.resolve(Container::getCommand, PodTemplateUtils::isNullOrEmpty)) //
                .withWorkingDir(h.resolve(Container::getWorkingDir, PodTemplateUtils::isNullOrEmpty)) //
                .withArgs(h.resolve(Container::getArgs, PodTemplateUtils::isNullOrEmpty)) //
                .withTty(h.resolve(Container::getTty, Objects::isNull)) //
                .withNewResources() //
                .withRequests(Map.copyOf(combineResources(parent, template, ResourceRequirements::getRequests))) //
                .withLimits(Map.copyOf(combineResources(parent, template, ResourceRequirements::getLimits))) //
                .endResources() //
                .withEnv(combineEnvVars(parent, template)) //
                .withEnvFrom(combinedEnvFromSources(parent, template))
                .withVolumeMounts(List.copyOf(volumeMounts.values()));
        if ((privileged != null && privileged)
                || runAsUser != null
                || runAsGroup != null
                || combineCapabilities(parent, template) != null) {
            containerBuilder = containerBuilder
                    .withNewSecurityContextLike(parent.getSecurityContext())
                    .withPrivileged(privileged)
                    .withRunAsUser(runAsUser)
                    .withRunAsGroup(runAsGroup)
                    .withCapabilities(combineCapabilities(parent, template))
                    .endSecurityContext();
        }
        return containerBuilder.build();
    }

    private static Map<String, Quantity> combineResources(
            Container parent,
            Container template,
            Function<ResourceRequirements, Map<String, Quantity>> resourceTypeMapper) {
        return Stream.of(template.getResources(), parent.getResources()) //
                .filter(Objects::nonNull) //
                .map(resourceTypeMapper) //
                .filter(Objects::nonNull) //
                .map(Map::entrySet) //
                .flatMap(Collection::stream) //
                .collect(
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1) // v2 (parent) loses
                        );
    }

    private static Capabilities combineCapabilities(Container parent, Container template) {
        Capabilities parentCapabilities = parent.getSecurityContext() != null
                ? parent.getSecurityContext().getCapabilities()
                : null;
        Capabilities templateCapabilities = template.getSecurityContext() != null
                ? template.getSecurityContext().getCapabilities()
                : null;
        if (parentCapabilities == null && templateCapabilities == null) {
            return null;
        }
        if (parentCapabilities == null) {
            return templateCapabilities;
        }
        if (templateCapabilities == null) {
            return parentCapabilities;
        }
        Capabilities combined = new Capabilities();
        combined.setAdd(combineCapabilities(parentCapabilities, templateCapabilities, Capabilities::getAdd));
        combined.setDrop(combineCapabilities(parentCapabilities, templateCapabilities, Capabilities::getDrop));
        return combined;
    }

    private static List<String> combineCapabilities(
            Capabilities parentCapabilities,
            Capabilities templateCapabilities,
            Function<Capabilities, List<String>> capabilitiesListFunction) {
        List<String> parentCapabilitiesList = capabilitiesListFunction.apply(parentCapabilities);
        List<String> templateCapabilitiesList = capabilitiesListFunction.apply(templateCapabilities);
        // override: template capabilities win
        if (templateCapabilitiesList != null) {
            return templateCapabilitiesList;
        }
        return parentCapabilitiesList;
    }

    /**
     * Combines all given pods together in order.
     * @param pods the pods to combine
     */
    public static Pod combine(List<Pod> pods) {
        Pod result = null;
        for (Pod p : pods) {
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
        if (template == null) {
            throw new IllegalArgumentException("Pod template should not be null");
        }
        if (parent == null) {
            return template;
        }

        LOGGER.finest(() -> "Combining pods, parent: " + Serialization2.asYaml(parent) + " template: "
                + Serialization2.asYaml(template));

        Map<String, String> nodeSelector =
                mergeMaps(parent.getSpec().getNodeSelector(), template.getSpec().getNodeSelector());
        var h = new HierarchyResolver<>(parent.getSpec(), template.getSpec());

        Map<String, String> podAnnotations = mergeMaps(
                parent.getMetadata().getAnnotations(), template.getMetadata().getAnnotations());
        Map<String, String> podLabels = mergeMaps(
                parent.getMetadata().getLabels(), template.getMetadata().getLabels());

        Set<LocalObjectReference> imagePullSecrets = new LinkedHashSet<>();
        imagePullSecrets.addAll(parent.getSpec().getImagePullSecrets());
        imagePullSecrets.addAll(template.getSpec().getImagePullSecrets());

        // Containers
        List<Container> combinedContainers = combineContainers(
                parent.getSpec().getContainers(), template.getSpec().getContainers());

        // Init containers
        List<Container> combinedInitContainers = combineContainers(
                parent.getSpec().getInitContainers(), template.getSpec().getInitContainers());

        // Volumes
        List<Volume> combinedVolumes =
                combineVolumes(parent.getSpec().getVolumes(), template.getSpec().getVolumes());

        // Tolerations
        List<Toleration> combinedTolerations = new LinkedList<>();
        Optional.ofNullable(parent.getSpec().getTolerations()).ifPresent(combinedTolerations::addAll);
        Optional.ofNullable(template.getSpec().getTolerations()).ifPresent(combinedTolerations::addAll);

        //        WorkspaceVolume workspaceVolume = template.isCustomWorkspaceVolumeEnabled() &&
        // template.getWorkspaceVolume() != null ? template.getWorkspaceVolume() : parent.getWorkspaceVolume();

        // Tool location node properties
        //        List<ToolLocationNodeProperty> toolLocationNodeProperties = new ArrayList<>();
        //        toolLocationNodeProperties.addAll(parent.getNodeProperties());
        //        toolLocationNodeProperties.addAll(template.getNodeProperties());

        var metadataBuilder = new PodBuilder(parent)
                .withNewMetadataLike(parent.getMetadata()) //
                .withAnnotations(podAnnotations)
                .withLabels(podLabels);
        if (!isNullOrEmpty(template.getMetadata().getName())) {
            metadataBuilder.withName(template.getMetadata().getName());
        }
        if (!isNullOrEmpty(template.getMetadata().getNamespace())) {
            metadataBuilder.withNamespace(template.getMetadata().getNamespace());
        }

        var specBuilder = metadataBuilder
                .endMetadata() //
                .withNewSpecLike(parent.getSpec()) //
                .withNodeSelector(nodeSelector) //
                .withServiceAccount(h.resolve(PodSpec::getServiceAccount, PodTemplateUtils::isNullOrEmpty)) //
                .withServiceAccountName(h.resolve(PodSpec::getServiceAccountName, PodTemplateUtils::isNullOrEmpty)) //
                .withSchedulerName(h.resolve(PodSpec::getSchedulerName, PodTemplateUtils::isNullOrEmpty))
                .withActiveDeadlineSeconds(h.resolve(PodSpec::getActiveDeadlineSeconds, Objects::isNull)) //
                .withHostNetwork(h.resolve(PodSpec::getHostNetwork, Objects::isNull)) //
                .withShareProcessNamespace(h.resolve(PodSpec::getShareProcessNamespace, Objects::isNull)) //
                .withContainers(combinedContainers) //
                .withInitContainers(combinedInitContainers) //
                .withVolumes(combinedVolumes) //
                .withTolerations(combinedTolerations) //
                .withImagePullSecrets(new ArrayList<>(imagePullSecrets));

        // Security context
        if (template.getSpec().getSecurityContext() != null || parent.getSpec().getSecurityContext() != null) {
            specBuilder
                    .editOrNewSecurityContext()
                    .withRunAsUser(
                            template.getSpec().getSecurityContext() != null
                                            && template.getSpec()
                                                            .getSecurityContext()
                                                            .getRunAsUser()
                                                    != null
                                    ? template.getSpec().getSecurityContext().getRunAsUser()
                                    : (parent.getSpec().getSecurityContext() != null
                                                    && parent.getSpec()
                                                                    .getSecurityContext()
                                                                    .getRunAsUser()
                                                            != null
                                            ? parent.getSpec()
                                                    .getSecurityContext()
                                                    .getRunAsUser()
                                            : null))
                    .withRunAsGroup(
                            template.getSpec().getSecurityContext() != null
                                            && template.getSpec()
                                                            .getSecurityContext()
                                                            .getRunAsGroup()
                                                    != null
                                    ? template.getSpec().getSecurityContext().getRunAsGroup()
                                    : (parent.getSpec().getSecurityContext() != null
                                                    && parent.getSpec()
                                                                    .getSecurityContext()
                                                                    .getRunAsGroup()
                                                            != null
                                            ? parent.getSpec()
                                                    .getSecurityContext()
                                                    .getRunAsGroup()
                                            : null))
                    .endSecurityContext();
        }

        // podTemplate.setLabel(label);
        //        podTemplate.setEnvVars(combineEnvVars(parent, template));
        //        podTemplate.setWorkspaceVolume(workspaceVolume);
        //        podTemplate.setNodeProperties(toolLocationNodeProperties);
        //        podTemplate.setNodeUsageMode(nodeUsageMode);
        //        podTemplate.setYaml(template.getYaml() == null ? parent.getYaml() : template.getYaml());

        Pod pod = specBuilder.endSpec().build();
        LOGGER.finest(() -> "Pods combined: " + Serialization2.asYaml(pod));
        return pod;
    }

    @NonNull
    private static List<Container> combineContainers(List<Container> parent, List<Container> child) {
        LinkedHashMap<String, Container> combinedContainers = new LinkedHashMap<>(); // Need to retain insertion order
        Map<String, Container> parentContainers =
                parent.stream().collect(toMap(Container::getName, c -> c, throwingMerger(), LinkedHashMap::new));
        Map<String, Container> childContainers = child.stream()
                .collect(toMap(
                        Container::getName,
                        c -> combine(parentContainers.get(c.getName()), c),
                        throwingMerger(),
                        LinkedHashMap::new));
        combinedContainers.putAll(parentContainers);
        combinedContainers.putAll(childContainers);
        return new ArrayList<>(combinedContainers.values());
    }

    private static List<Volume> combineVolumes(@NonNull List<Volume> volumes1, @NonNull List<Volume> volumes2) {
        Map<String, Volume> volumesByName =
                volumes1.stream().collect(Collectors.toMap(Volume::getName, Function.identity()));
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
        if (template == null) {
            throw new IllegalArgumentException("Pod template should not be null");
        }
        if (parent == null) {
            return template;
        }

        LOGGER.log(Level.FINEST, () -> "Combining pod templates, parent: " + parent + ", template: " + template);

        String name = template.getName();
        String label = template.getLabel();

        Set<PodAnnotation> podAnnotations = new LinkedHashSet<>();
        podAnnotations.addAll(template.getAnnotations());
        podAnnotations.addAll(parent.getAnnotations());

        Set<PodImagePullSecret> imagePullSecrets = new LinkedHashSet<>();
        imagePullSecrets.addAll(parent.getImagePullSecrets());
        imagePullSecrets.addAll(template.getImagePullSecrets());

        Map<String, ContainerTemplate> combinedContainers = new HashMap<>();
        Map<String, PodVolume> combinedVolumes = new HashMap<>();

        // Containers
        Map<String, ContainerTemplate> parentContainers =
                parent.getContainers().stream().collect(toMap(ContainerTemplate::getName, c -> c));
        combinedContainers.putAll(parentContainers);
        combinedContainers.putAll(template.getContainers().stream()
                .collect(toMap(ContainerTemplate::getName, c -> combine(parentContainers.get(c.getName()), c))));

        // Volumes
        Map<String, PodVolume> parentVolumes =
                parent.getVolumes().stream().collect(toMap(PodVolume::getMountPath, v -> v));
        combinedVolumes.putAll(parentVolumes);
        combinedVolumes.putAll(template.getVolumes().stream().collect(toMap(PodVolume::getMountPath, v -> v)));

        WorkspaceVolume workspaceVolume =
                WorkspaceVolume.merge(parent.getWorkspaceVolume(), template.getWorkspaceVolume());

        // Tool location node properties
        List<NodeProperty<?>> nodeProperties = new ArrayList<>(parent.getNodeProperties());
        nodeProperties.addAll(template.getNodeProperties());

        PodTemplate podTemplate = new PodTemplate(template.getId());
        var h = new HierarchyResolver<>(parent, template);
        podTemplate.setName(name);
        podTemplate.setNamespace(h.resolve(PodTemplate::getNamespace, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setLabel(label);
        podTemplate.setNodeSelector(h.resolve(PodTemplate::getNodeSelector, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setServiceAccount(h.resolve(PodTemplate::getServiceAccount, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setSchedulerName(h.resolve(PodTemplate::getSchedulerName, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setEnvVars(combineEnvVars(parent, template));
        podTemplate.setContainers(new ArrayList<>(combinedContainers.values()));
        podTemplate.setWorkspaceVolume(workspaceVolume);
        podTemplate.setVolumes(new ArrayList<>(combinedVolumes.values()));
        podTemplate.setImagePullSecrets(new ArrayList<>(imagePullSecrets));
        podTemplate.setAnnotations(new ArrayList<>(podAnnotations));
        podTemplate.setNodeProperties(nodeProperties);
        podTemplate.setNodeUsageMode(
                template.getNodeUsageMode() == null ? parent.getNodeUsageMode() : template.getNodeUsageMode());
        podTemplate.setYamlMergeStrategy(h.resolve(
                PodTemplate::getYamlMergeStrategy,
                childValue -> childValue == null && parent.isInheritYamlMergeStrategy()));
        podTemplate.setInheritYamlMergeStrategy(parent.isInheritYamlMergeStrategy());
        podTemplate.setInheritFrom(h.resolve(PodTemplate::getInheritFrom, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setInstanceCap(h.resolve(PodTemplate::getInstanceCap, i -> Objects.equals(i, Integer.MAX_VALUE)));
        podTemplate.setSlaveConnectTimeout(h.resolve(
                PodTemplate::getSlaveConnectTimeout,
                i -> Objects.equals(i, PodTemplate.DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT)));
        podTemplate.setIdleMinutes(h.resolve(PodTemplate::getIdleMinutes, i -> Objects.equals(i, 0)));
        podTemplate.setActiveDeadlineSeconds(
                h.resolve(PodTemplate::getActiveDeadlineSeconds, i -> Objects.equals(i, 0)));
        podTemplate.setServiceAccount(h.resolve(PodTemplate::getServiceAccount, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setSchedulerName(h.resolve(PodTemplate::getSchedulerName, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setPodRetention(template.getPodRetention());
        podTemplate.setShowRawYaml(h.resolve(PodTemplate::isShowRawYaml, v -> template.isShowRawYamlSet()));
        podTemplate.setRunAsUser(h.resolve(PodTemplate::getRunAsUser, Objects::isNull));
        podTemplate.setRunAsGroup(h.resolve(PodTemplate::getRunAsGroup, Objects::isNull));
        podTemplate.setSupplementalGroups(h.resolve(PodTemplate::getSupplementalGroups, Objects::isNull));
        podTemplate.setAgentContainer(h.resolve(PodTemplate::getAgentContainer, PodTemplateUtils::isNullOrEmpty));
        podTemplate.setAgentInjection(h.resolve(PodTemplate::isAgentInjection, v -> template.isShowRawYamlSet()));
        podTemplate.setAgentInjection(h.resolve(PodTemplate::isAgentInjection, v -> template.isAgentInjectionSet()));
        if (template.isHostNetworkSet()) {
            podTemplate.setHostNetwork(template.isHostNetwork());
        } else if (parent.isHostNetworkSet()) {
            podTemplate.setHostNetwork(parent.isHostNetwork());
        }

        List<String> yamls = new ArrayList<>(parent.getYamls());
        yamls.addAll(template.getYamls());
        podTemplate.setYamls(yamls);
        podTemplate.setListener(template.getListener());

        LOGGER.log(Level.FINEST, "Pod templates combined: {0}", podTemplate);
        return podTemplate;
    }

    /**
     * Helps to resolve structure fields according to hierarchy.
     */
    private static class HierarchyResolver<P> {
        private final P parent;
        private final P child;

        HierarchyResolver(P parent, P child) {
            this.parent = parent;
            this.child = child;
        }

        /**
         * <p>Resolves a pod template field according to hierarchy.
         * <p>If the child pod template uses a non-default value, then it is used.
         * <p>Otherwise the parent value is used.
         * @param getter the getter function to obtain the field value
         * @param isDefaultValue A function to determine if the value is the default value
         * @return The value for the field taking into account the hierarchy.
         * @param <T> The field type
         */
        <T> T resolve(Function<P, T> getter, Predicate<T> isDefaultValue) {
            var childValue = getter.apply(child);
            return !isDefaultValue.test(childValue) ? childValue : getter.apply(parent);
        }
    }

    /**
     * Unwraps the hierarchy of the PodTemplate.
     *
     * @param template                   The template to unwrap.
     * @param defaultProviderTemplate    The name of the template that provides the default values.
     * @param allTemplates               A collection of all the known templates
     * @return
     */
    static PodTemplate unwrap(
            PodTemplate template, String defaultProviderTemplate, Collection<PodTemplate> allTemplates) {
        if (template == null) {
            return null;
        }

        List<String> inheritFrom = computedInheritFrom(template, defaultProviderTemplate);
        if (inheritFrom.isEmpty()) {
            return template;
        } else {
            PodTemplate parent = null;
            for (String name : inheritFrom) {
                PodTemplate next = getTemplateByName(name, allTemplates);
                if (next != null) {
                    parent = combine(parent, unwrap(next, allTemplates));
                }
            }
            PodTemplate combined = combine(parent, template);
            combined.setUnwrapped(true);
            LOGGER.log(Level.FINEST, "Combined parent + template is {0}", combined);
            return combined;
        }
    }

    private static List<String> computedInheritFrom(PodTemplate template, String defaultProviderTemplate) {
        List<String> hierarchy = new ArrayList<>();
        if (!isNullOrEmpty(defaultProviderTemplate)) {
            hierarchy.add(defaultProviderTemplate);
        }
        if (!isNullOrEmpty(template.getInheritFrom())) {
            String[] split = template.getInheritFrom().split(" +");
            for (String name : split) {
                hierarchy.add(name);
            }
        }
        return Collections.unmodifiableList(hierarchy);
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
    @CheckForNull
    public static PodTemplate getTemplateByLabel(@CheckForNull Label label, Collection<PodTemplate> templates) {
        for (PodTemplate t : templates) {
            if ((label == null && t.getNodeUsageMode() == Node.Mode.NORMAL)
                    || (label != null && label.matches(t.getLabelSet()))) {
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
        return isNullOrEmpty(s) ? defaultValue : replaceMacro(s, properties);
    }

    public static Pod parseFromYaml(String yaml) {
        String s = yaml;
        // JENKINS-57116
        if (StringUtils.isBlank(s)) {
            LOGGER.log(Level.WARNING, "[JENKINS-57116] Trying to parse invalid yaml: \"{0}\"", yaml);
            s = "{}";
        }
        Pod podFromYaml;
        try (InputStream is = new ByteArrayInputStream(s.getBytes(UTF_8))) {
            podFromYaml = Serialization2.unmarshal(is, Pod.class);
            //            podFromYaml = new KubernetesSerialization().unmarshal(is, Pod.class);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to parse yaml: \"%s\"", yaml), e);
        }
        LOGGER.finest(() -> "Parsed pod template from yaml: " + Serialization2.asYaml(podFromYaml));
        // yaml can be just a fragment, avoid NPEs
        if (podFromYaml.getMetadata() == null) {
            podFromYaml.setMetadata(new ObjectMeta());
        }
        if (podFromYaml.getSpec() == null) {
            podFromYaml.setSpec(new PodSpec());
        }
        return podFromYaml;
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
        return StringUtils.isBlank(label)
                ? true
                : label.length() <= 63 && LABEL_VALIDATION.matcher(label).matches();
    }

    /** TODO perhaps enforce https://docs.docker.com/engine/reference/commandline/tag/#extended-description */
    public static boolean validateImage(String image) {
        return image != null && image.matches("\\S+");
    }

    /**
     * <p>Sanitizes the input string to create a valid Kubernetes label.
     * <p>The input string is truncated to a maximum length of 57 characters,
     * and any characters that are not alphanumeric or hyphens are replaced with underscores. If the input string starts with a non-alphanumeric
     * character, it is replaced with 'x'.
     *
     * @param  input  the input string to be sanitized
     * @return        the sanitized and validated label
     * @throws AssertionError if the generated label is not valid
     */
    public static String sanitizeLabel(@CheckForNull String input) {
        if (input == null) {
            return null;
        }
        int max = /* Kubernetes limit */ 63 - /* hyphen */ 1 - /* suffix */ 5;
        String label;
        if (input.length() > max) {
            label = input.substring(input.length() - max);
        } else {
            label = input;
        }
        label = label.replaceAll("[^_a-zA-Z0-9-]", "_")
                .replaceFirst("^[^a-zA-Z0-9]", "x")
                .replaceFirst("[^a-zA-Z0-9]$", "x");

        assert PodTemplateUtils.validateLabel(label) : label;
        return label;
    }

    private static List<EnvVar> combineEnvVars(Container parent, Container template) {
        Map<String, EnvVar> combinedEnvVars = new HashMap<>();
        Stream.of(parent.getEnv(), template.getEnv())
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(var -> !isNullOrEmpty(var.getName()))
                .forEachOrdered(var -> combinedEnvVars.put(var.getName(), var));
        return new ArrayList<>(combinedEnvVars.values());
    }

    private static List<TemplateEnvVar> combineEnvVars(ContainerTemplate parent, ContainerTemplate template) {
        return combineEnvVars(parent.getEnvVars(), template.getEnvVars());
    }

    private static List<TemplateEnvVar> combineEnvVars(PodTemplate parent, PodTemplate template) {
        return combineEnvVars(parent.getEnvVars(), template.getEnvVars());
    }

    private static List<TemplateEnvVar> combineEnvVars(List<TemplateEnvVar> parent, List<TemplateEnvVar> child) {
        Map<String, TemplateEnvVar> combinedEnvVars =
                mergeMaps(templateEnvVarstoMap(parent), templateEnvVarstoMap(child));
        return combinedEnvVars.entrySet().stream()
                .filter(entry -> !isNullOrEmpty(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(toList());
    }

    static Map<String, TemplateEnvVar> templateEnvVarstoMap(List<TemplateEnvVar> envVarList) {
        return envVarList.stream()
                .collect(Collectors.toMap(
                        TemplateEnvVar::getKey, Function.identity(), throwingMerger(), LinkedHashMap::new));
    }

    private static <T> BinaryOperator<T> throwingMerger() {
        return (u, v) -> {
            throw new IllegalStateException(String.format("Duplicate key %s", u));
        };
    }

    private static List<EnvFromSource> combinedEnvFromSources(Container parent, Container template) {
        List<EnvFromSource> combinedEnvFromSources = new ArrayList<>();
        combinedEnvFromSources.addAll(parent.getEnvFrom());
        combinedEnvFromSources.addAll(template.getEnvFrom());
        return combinedEnvFromSources.stream()
                .filter(envFromSource -> envFromSource.getConfigMapRef() != null
                                && !isNullOrEmpty(
                                        envFromSource.getConfigMapRef().getName())
                        || envFromSource.getSecretRef() != null
                                && !isNullOrEmpty(envFromSource.getSecretRef().getName()))
                .collect(toList());
    }

    private static <K, V> Map<K, V> mergeMaps(Map<K, V> m1, Map<K, V> m2) {
        Map<K, V> m = new LinkedHashMap<>();
        if (m1 != null) m.putAll(m1);
        if (m2 != null) m.putAll(m2);
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

    public static boolean isNullOrEmpty(@Nullable String string) {
        return string == null || string.isEmpty();
    }

    public static boolean isNullOrEmpty(@Nullable List<?> list) {
        return list == null || list.isEmpty();
    }

    public static @Nullable String emptyToNull(@Nullable String string) {
        return isNullOrEmpty(string) ? null : string;
    }
}
