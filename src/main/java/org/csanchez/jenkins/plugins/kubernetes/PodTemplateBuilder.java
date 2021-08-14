/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.csanchez.jenkins.plugins.kubernetes;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import io.fabric8.kubernetes.api.model.PodSpecFluent;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateStepExecution;
import org.csanchez.jenkins.plugins.kubernetes.pod.decorator.PodDecorator;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.TcpSlaveAgentListener;
import hudson.slaves.SlaveComputer;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodFluent.MetadataNested;
import io.fabric8.kubernetes.api.model.PodFluent.SpecNested;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;

import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud.JNLP_NAME;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.combine;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.isNullOrEmpty;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.substituteEnv;
import org.csanchez.jenkins.plugins.kubernetes.volumes.ConfigMapVolume;

/**
 * Helper class to build Pods from PodTemplates
 * 
 * @author Carlos Sanchez
 * @since
 *
 */
public class PodTemplateBuilder {

    private static final Logger LOGGER = Logger.getLogger(PodTemplateBuilder.class.getName());

    private static final Pattern SPLIT_IN_SPACES = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    private static final String WORKSPACE_VOLUME_NAME = "workspace-volume";

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "tests")
    @Restricted(NoExternalUse.class)
    static String DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".dockerRegistryPrefix");
    @Restricted(NoExternalUse.class)
    static final String DEFAULT_JNLP_IMAGE = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultImage", "jenkins/inbound-agent:4.3-4");

    static final String DEFAULT_JNLP_CONTAINER_MEMORY_REQUEST = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultContainer.defaultMemoryRequest", "256Mi");
    static final String DEFAULT_JNLP_CONTAINER_CPU_REQUEST = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultContainer.defaultCpuRequest", "100m");

    static final String DEFAULT_JNLP_CONTAINER_MEMORY_LIMIT = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultContainer.defaultMemoryLimit");
    static final String DEFAULT_JNLP_CONTAINER_CPU_LIMIT = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultContainer.defaultCpuLimit");

    private static final String JNLPMAC_REF = "\\$\\{computer.jnlpmac\\}";
    private static final String NAME_REF = "\\$\\{computer.name\\}";

    private PodTemplate template;

    @CheckForNull
    private KubernetesSlave agent;

    @CheckForNull
    private KubernetesCloud cloud;

    @Deprecated
    public PodTemplateBuilder(PodTemplate template) {
        this.template = template;
    }

    public PodTemplateBuilder(PodTemplate template, KubernetesSlave agent) {
        this.template = template;
        this.agent = agent;
        this.cloud = agent.getKubernetesCloud();
    }

    public PodTemplateBuilder withSlave(@Nonnull KubernetesSlave slave) {
        this.agent = slave;
        this.cloud = slave.getKubernetesCloud();
        return this;
    }

    @Deprecated
    public Pod build(KubernetesSlave slave) {
        LOGGER.log(Level.WARNING, "This method is deprecated and does nothing");
        return this.build();
    }

    /**
     * Create a Pod object from a PodTemplate
     */
    public Pod build() {

        // Build volumes and volume mounts.
        Map<String, Volume> volumes = new HashMap<>();
        Map<String, VolumeMount> volumeMounts = new HashMap<>();

        int i = 0;
        for (final PodVolume volume : template.getVolumes()) {
            final String volumeName = "volume-" + i;
            //We need to normalize the path or we can end up in really hard to debug issues.
            final String mountPath = substituteEnv(Paths.get(volume.getMountPath()).normalize().toString().replace("\\", "/"));
            if (!volumeMounts.containsKey(mountPath)) {
                VolumeMountBuilder volumeMountBuilder = new VolumeMountBuilder() //
                        .withMountPath(mountPath).withName(volumeName).withReadOnly(false);

                if (volume instanceof ConfigMapVolume) {
                    final ConfigMapVolume configmapVolume = (ConfigMapVolume) volume;
                        //We need to normalize the subPath or we can end up in really hard to debug issues Just in case.
                        final String subPath = substituteEnv(Paths.get(configmapVolume.getSubPath()).normalize().toString().replace("\\", "/"));
                        volumeMountBuilder = volumeMountBuilder.withSubPath(subPath);
                            }
                volumeMounts.put(mountPath, volumeMountBuilder.build());
                volumes.put(volumeName, volume.buildVolume(volumeName));
                i++;
            }
        }

        volumes.put(WORKSPACE_VOLUME_NAME, template.getWorkspaceVolume().buildVolume(WORKSPACE_VOLUME_NAME, agent != null ? agent.getPodName() : null));

        Map<String, Container> containers = new HashMap<>();
        // containers from pod template
        for (ContainerTemplate containerTemplate : template.getContainers()) {
            containers.put(containerTemplate.getName(),
                    createContainer(containerTemplate, template.getEnvVars(), volumeMounts.values()));
        }

        MetadataNested<PodBuilder> metadataBuilder = new PodBuilder().withNewMetadata();
        if (agent != null) {
            metadataBuilder.withName(agent.getPodName());
        }

        Map<String, String> labels = new HashMap<>();
        if (agent != null) {
            labels.putAll(agent.getKubernetesCloud().getPodLabelsMap());
        }
        labels.putAll(template.getLabelsMap());
        if (!labels.isEmpty()) {
            metadataBuilder.withLabels(labels);
        }

        Map<String, String> annotations = getAnnotationsMap(template.getAnnotations());
        if (!annotations.isEmpty()) {
            metadataBuilder.withAnnotations(annotations);
        }

        SpecNested<PodBuilder> builder = metadataBuilder.endMetadata().withNewSpec();

        if (template.getActiveDeadlineSeconds() > 0) {
            builder = builder.withActiveDeadlineSeconds(Long.valueOf(template.getActiveDeadlineSeconds()));
        }

        if (!volumes.isEmpty()) {
            builder.withVolumes(volumes.values().toArray(new Volume[volumes.size()]));
        }
        if (template.getServiceAccount() != null) {
            builder.withServiceAccountName(substituteEnv(template.getServiceAccount()));
        }
        if (template.getSchedulerName() != null) {
            builder.withSchedulerName(substituteEnv(template.getSchedulerName()));
        }

        List<LocalObjectReference> imagePullSecrets = template.getImagePullSecrets().stream()
                .map((x) -> x.toLocalObjectReference()).collect(Collectors.toList());
        if (!imagePullSecrets.isEmpty()) {
            builder.withImagePullSecrets(imagePullSecrets);
        }

        Map<String, String> nodeSelector = getNodeSelectorMap(template.getNodeSelector());
        if (!nodeSelector.isEmpty()) {
            builder.withNodeSelector(nodeSelector);
        }

        if (template.getTerminationGracePeriodSeconds() != null) {
            builder.withTerminationGracePeriodSeconds(template.getTerminationGracePeriodSeconds());
        }
        builder.withContainers(containers.values().toArray(new Container[containers.size()]));

        Long runAsUser = template.getRunAsUserAsLong();
        Long runAsGroup = template.getRunAsGroupAsLong();
        String supplementalGroups = template.getSupplementalGroups();
        if (runAsUser != null || runAsGroup != null || supplementalGroups != null) {
            PodSpecFluent.SecurityContextNested<SpecNested<PodBuilder>> securityContext = builder.editOrNewSecurityContext();
            if (runAsUser != null) {
                securityContext.withRunAsUser(runAsUser);
            }
            if (runAsGroup != null) {
                securityContext.withRunAsGroup(runAsGroup);
            }
            if (supplementalGroups != null) {
                securityContext.withSupplementalGroups(parseSupplementalGroupList(supplementalGroups));
            }
            securityContext.endSecurityContext();
        }

        if (template.isHostNetworkSet()) {
            builder.withHostNetwork(template.isHostNetwork());
        }

        // merge with the yaml fragments
        Pod pod = combine(template.getYamlsPod(), builder.endSpec().build());

        // Apply defaults

        // default jnlp container
        Optional<Container> jnlpOpt = pod.getSpec().getContainers().stream().filter(c -> JNLP_NAME.equals(c.getName()))
                .findFirst();
        Container jnlp = jnlpOpt.orElse(new ContainerBuilder().withName(JNLP_NAME)
                .withVolumeMounts(volumeMounts.values().toArray(new VolumeMount[volumeMounts.values().size()])).build());
        if (!jnlpOpt.isPresent()) {
            pod.getSpec().getContainers().add(jnlp);
        }
        pod.getSpec().getContainers().stream().filter(c -> c.getWorkingDir() == null).forEach(c -> c.setWorkingDir(jnlp.getWorkingDir()));
        if (StringUtils.isBlank(jnlp.getImage())) {
            String jnlpImage = DEFAULT_JNLP_IMAGE;
            if (StringUtils.isNotEmpty(DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX)) {
                jnlpImage = Util.ensureEndsWith(DEFAULT_JNLP_DOCKER_REGISTRY_PREFIX, "/") + jnlpImage;
            }
            jnlp.setImage(jnlpImage);
        }
        Map<String, EnvVar> envVars = new HashMap<>();
        envVars.putAll(jnlpEnvVars(jnlp.getWorkingDir()));
        envVars.putAll(defaultEnvVars(template.getEnvVars()));
        envVars.putAll(jnlp.getEnv().stream().collect(Collectors.toMap(EnvVar::getName, Function.identity())));
        jnlp.setEnv(new ArrayList<>(envVars.values()));
        if (jnlp.getResources() == null) {

            Map<String, Quantity> reqMap = new HashMap<>();
            Map<String, Quantity> limMap = new HashMap<>();
            reqMap.put("cpu", new Quantity(DEFAULT_JNLP_CONTAINER_CPU_REQUEST));
            reqMap.put("memory", new Quantity(DEFAULT_JNLP_CONTAINER_MEMORY_REQUEST));

            if (DEFAULT_JNLP_CONTAINER_CPU_LIMIT!=null) {
                limMap.put("cpu", new Quantity(DEFAULT_JNLP_CONTAINER_CPU_LIMIT));
            }

            if (DEFAULT_JNLP_CONTAINER_MEMORY_LIMIT!=null) {
                limMap.put("memory", new Quantity(DEFAULT_JNLP_CONTAINER_MEMORY_LIMIT));
            }

            ResourceRequirements reqs = new ResourceRequirementsBuilder()
                        .withRequests(reqMap)
                        .withLimits(limMap)
                        .build();

            jnlp.setResources(reqs);

        }
        if (cloud != null) {
            pod = PodDecorator.decorateAll(cloud, pod);
        }
        Pod finalPod = pod;
        LOGGER.finest(() -> "Pod built: " + Serialization.asYaml(finalPod));
        return pod;
    }

    private Map<String, EnvVar> defaultEnvVars(Collection<TemplateEnvVar> globalEnvVars) {
        Map<String, String> env = new HashMap<>();
        if (agent != null) {
            KubernetesCloud cloud = agent.getKubernetesCloud();
            if (cloud.isAddMasterProxyEnvVars()) {
                // see if the env vars for proxy that the remoting.jar looks for
                // are set on the controller, and if so, propagate them to the agent
                // vs. having to set on each pod template; if explicitly set already
                // the processing of globalEnvVars below will override;
                // see org.jenkinsci.remoting.engine.JnlpAgentEndpointResolver
                String noProxy = System.getenv("no_proxy");
                if (!StringUtils.isBlank(noProxy)) {
                    env.put("no_proxy", noProxy);
                }
                String httpProxy = System.getenv("http_proxy");
                if (!StringUtils.isBlank(httpProxy)) {
                    env.put("http_proxy", httpProxy);
                }
            }
        }
        Map<String, EnvVar> envVarsMap = new HashMap<>();

        env.entrySet().forEach(item ->
                envVarsMap.put(item.getKey(), new EnvVar(item.getKey(), item.getValue(), null))
        );

        if (globalEnvVars != null) {
            globalEnvVars.forEach(item ->
                    envVarsMap.put(item.getKey(), item.buildEnvVar())
            );
        }
        return envVarsMap;
    }

    private Map<String, EnvVar> jnlpEnvVars(String workingDir) {
        if (workingDir == null) {
            workingDir = ContainerTemplate.DEFAULT_WORKING_DIR;
        }
        // Last-write wins map of environment variable names to values
        HashMap<String, String> env = new HashMap<>();

        if (agent != null) {
            SlaveComputer computer = agent.getComputer();
            if (computer != null) {
                // Add some default env vars for Jenkins
                env.put("JENKINS_SECRET", computer.getJnlpMac());
                // JENKINS_AGENT_NAME is default in jnlp-slave
                // JENKINS_NAME only here for backwords compatability
                env.put("JENKINS_NAME", computer.getName());
                env.put("JENKINS_AGENT_NAME", computer.getName());
            } else {
                LOGGER.log(Level.INFO, "Computer is null for agent: {0}", agent.getNodeName());
            }

            env.put("JENKINS_AGENT_WORKDIR", workingDir);

            KubernetesCloud cloud = agent.getKubernetesCloud();

            if (!StringUtils.isBlank(cloud.getJenkinsTunnel())) {
                env.put("JENKINS_TUNNEL", cloud.getJenkinsTunnel());
            }

            if (!cloud.isDirectConnection()) {
                env.put("JENKINS_URL", cloud.getJenkinsUrlOrDie());
                if (cloud.isWebSocket()) {
                    env.put("JENKINS_WEB_SOCKET", "true");
                }
            } else {
                TcpSlaveAgentListener tcpSlaveAgentListener = Jenkins.get().getTcpSlaveAgentListener();
                String host = tcpSlaveAgentListener.getAdvertisedHost();
                int port = tcpSlaveAgentListener.getAdvertisedPort();
                env.put("JENKINS_DIRECT_CONNECTION", host + ":" + port);
                env.put("JENKINS_PROTOCOLS", "JNLP4-connect");
                env.put("JENKINS_INSTANCE_IDENTITY", tcpSlaveAgentListener.getIdentityPublicKey());
            }

        }
        Map<String, EnvVar> envVarsMap = new HashMap<>();

        env.entrySet().forEach(item ->
                envVarsMap.put(item.getKey(), new EnvVar(item.getKey(), item.getValue(), null))
        );
        return envVarsMap;
    }

    private Container createContainer(ContainerTemplate containerTemplate, Collection<TemplateEnvVar> globalEnvVars,
            Collection<VolumeMount> volumeMounts) {
        Map<String, EnvVar> envVarsMap = new HashMap<>();
        String workingDir = substituteEnv(containerTemplate.getWorkingDir());
        if (JNLP_NAME.equals(containerTemplate.getName())) {
            envVarsMap.putAll(jnlpEnvVars(workingDir));
        }
        envVarsMap.putAll(defaultEnvVars(globalEnvVars));

        if (containerTemplate.getEnvVars() != null) {
            containerTemplate.getEnvVars().forEach(item ->
                    envVarsMap.put(item.getKey(), item.buildEnvVar())
            );
        }

        EnvVar[] envVars = envVarsMap.values().stream().toArray(EnvVar[]::new);

        String cmd = containerTemplate.getArgs();
        if (agent != null && cmd != null) {
            SlaveComputer computer = agent.getComputer();
            if (computer != null) {
                cmd = cmd.replaceAll(JNLPMAC_REF, computer.getJnlpMac()) //
                        .replaceAll(NAME_REF, computer.getName());
            }
        }
        List<String> arguments = isNullOrEmpty(containerTemplate.getArgs()) ? Collections.emptyList()
                : parseDockerCommand(cmd);

        ContainerPort[] ports = containerTemplate.getPorts().stream().map(entry -> entry.toPort()).toArray(size -> new ContainerPort[size]);


        List<VolumeMount> containerMounts = getContainerVolumeMounts(volumeMounts, workingDir);

        ContainerLivenessProbe clp = containerTemplate.getLivenessProbe();
        Probe livenessProbe = null;
        if (clp != null && parseLivenessProbe(clp.getExecArgs()) != null) {
            livenessProbe = new ProbeBuilder()
                    .withExec(new ExecAction(parseLivenessProbe(clp.getExecArgs())))
                    .withInitialDelaySeconds(clp.getInitialDelaySeconds())
                    .withTimeoutSeconds(clp.getTimeoutSeconds())
                    .withFailureThreshold(clp.getFailureThreshold())
                    .withPeriodSeconds(clp.getPeriodSeconds())
                    .withSuccessThreshold(clp.getSuccessThreshold())
                    .build();
        }

        ContainerBuilder containerBuilder = new ContainerBuilder()
                .withName(substituteEnv(containerTemplate.getName()))
                .withImage(substituteEnv(containerTemplate.getImage()))
                .withImagePullPolicy(containerTemplate.isAlwaysPullImage() ? "Always" : "IfNotPresent");
        if (containerTemplate.isPrivileged() || containerTemplate.getRunAsUserAsLong() != null || containerTemplate.getRunAsGroupAsLong() != null) {
            containerBuilder = containerBuilder.withNewSecurityContext()
                    .withPrivileged(containerTemplate.isPrivileged())
                    .withRunAsUser(containerTemplate.getRunAsUserAsLong())
                    .withRunAsGroup(containerTemplate.getRunAsGroupAsLong())
                .endSecurityContext();
        }
        return containerBuilder
                .withWorkingDir(workingDir)
                .withVolumeMounts(containerMounts.toArray(new VolumeMount[containerMounts.size()]))
                .addToEnv(envVars)
                .addToPorts(ports)
                .withCommand(parseDockerCommand(containerTemplate.getCommand()))
                .withArgs(arguments)
                .withLivenessProbe(livenessProbe)
                .withTty(containerTemplate.isTtyEnabled())
                .withNewResources()
                .withRequests(getResourcesMap(containerTemplate.getResourceRequestMemory(), containerTemplate.getResourceRequestCpu(),containerTemplate.getResourceRequestEphemeralStorage()))
                .withLimits(getResourcesMap(containerTemplate.getResourceLimitMemory(), containerTemplate.getResourceLimitCpu(), containerTemplate.getResourceLimitEphemeralStorage()))
                .endResources()
                .build();
    }

    private VolumeMount getDefaultVolumeMount(@CheckForNull String workingDir) {
        String wd = workingDir;
        if (wd == null) {
            wd = ContainerTemplate.DEFAULT_WORKING_DIR;
            LOGGER.log(Level.FINE, "Container workingDir is null, defaulting to {0}", wd);
        }
        return new VolumeMountBuilder().withMountPath(wd).withName(WORKSPACE_VOLUME_NAME).withReadOnly(false).build();
    }

    private List<VolumeMount> getContainerVolumeMounts(Collection<VolumeMount> volumeMounts, String workingDir) {
        List<VolumeMount> containerMounts = new ArrayList<>(volumeMounts);
        if (!isNullOrEmpty(workingDir) && !PodVolume.volumeMountExists(workingDir, volumeMounts)) {
            containerMounts.add(getDefaultVolumeMount(workingDir));
        }
        return containerMounts;
    }

    /**
     * Split a command in the parts that Docker need
     *
     * @param dockerCommand
     * @return
     */
    @Restricted(NoExternalUse.class)
    static List<String> parseDockerCommand(String dockerCommand) {
        if (dockerCommand == null || dockerCommand.isEmpty()) {
            return null;
        }
        // handle quoted arguments
        Matcher m = SPLIT_IN_SPACES.matcher(dockerCommand);
        List<String> commands = new ArrayList<String>();
        while (m.find()) {
            commands.add(substituteEnv(m.group(1).replace("\"", "")));
        }
        return commands;
    }

    /**
     * Split a command in the parts that LivenessProbe need
     *
     * @param livenessProbeExec
     * @return
     */
    @Restricted(NoExternalUse.class)
    static List<String> parseLivenessProbe(String livenessProbeExec) {
        if (StringUtils.isBlank(livenessProbeExec)) {
            return null;
        }
        // handle quoted arguments
        Matcher m = SPLIT_IN_SPACES.matcher(livenessProbeExec);
        List<String> commands = new ArrayList<String>();
        while (m.find()) {
            commands.add(substituteEnv(m.group(1).replace("\"", "").replace("?:\\\"", "")));
        }
        return commands;
    }

    private Map<String, Quantity> getResourcesMap(String memory, String cpu, String ephemeralStorage) {
        Map<String, Quantity> builder = new HashMap<>();
        String actualMemory = substituteEnv(memory);
        String actualCpu = substituteEnv(cpu);
        String actualEphemeralStorage = substituteEnv(ephemeralStorage);
        if (StringUtils.isNotBlank(actualMemory)) {
            Quantity memoryQuantity = new Quantity(actualMemory);
            builder.put("memory", memoryQuantity);
        }
        if (StringUtils.isNotBlank(actualCpu)) {
            Quantity cpuQuantity = new Quantity(actualCpu);
            builder.put("cpu", cpuQuantity);
        }
        if (StringUtils.isNotBlank(actualEphemeralStorage)) {
            Quantity ephemeralStorageQuantity = new Quantity(actualEphemeralStorage);
            builder.put("ephemeral-storage", ephemeralStorageQuantity);
        }
        return Collections.unmodifiableMap(builder);
    }

    private Map<String, String> getAnnotationsMap(List<PodAnnotation> annotations) {
        Map<String, String> builder = new HashMap<>();
        if (annotations != null) {
            for (PodAnnotation podAnnotation : annotations) {
                builder.put(podAnnotation.getKey(), substituteEnv(podAnnotation.getValue()));
            }
        }
        return Collections.unmodifiableMap(builder);
    }

    private Map<String, String> getNodeSelectorMap(String selectors) {
        if (isNullOrEmpty(selectors)) {
            return Collections.EMPTY_MAP;
        } else {
            Map<String, String> builder = new HashMap<>();

            for (String selector : selectors.split(",")) {
                String[] parts = selector.split("=");
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    builder.put(parts[0], substituteEnv(parts[1]));
                } else {
                    LOGGER.log(Level.WARNING, "Ignoring selector '" + selector
                            + "'. Selectors must be in the format 'label1=value1,label2=value2'.");
                }
            }
            return Collections.unmodifiableMap(builder);
        }
    }

    private List<Long> parseSupplementalGroupList(String gids) {
        if (isNullOrEmpty(gids)) {
            return Collections.EMPTY_LIST;
        }
        List<Long>builder = new ArrayList<>();
        for (String gid : gids.split(",")) {
            try {
                if (!isNullOrEmpty(gid)) {
                    builder.add(Long.parseLong(gid));
                } else {
                    LOGGER.log(Level.WARNING, "Ignoring GID '{0}'. Group ID's cannot be empty or null.", gid);
                }
            } catch (NumberFormatException nfe) {
                LOGGER.log(Level.WARNING, "Ignoring GID '{0}'. Group ID's must be valid longs.", gid);
            }
        }
        return Collections.unmodifiableList(builder);
    }
}
