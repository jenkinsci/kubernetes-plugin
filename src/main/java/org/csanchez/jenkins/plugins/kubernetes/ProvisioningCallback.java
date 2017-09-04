/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud.*;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.*;

import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateStepExecution;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.ExecAction;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * Callback for Kubernetes cloud provision
 * 
 * @since 0.13
 */
class ProvisioningCallback implements Callable<Node> {

    private static final Logger LOGGER = Logger.getLogger(ProvisioningCallback.class.getName());

    private static final Pattern SPLIT_IN_SPACES = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    private static final String WORKSPACE_VOLUME_NAME = "workspace-volume";

    private static final String DEFAULT_JNLP_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private static final String DEFAULT_JNLP_IMAGE = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultImage", "jenkinsci/jnlp-slave:alpine");

    private static final String JNLPMAC_REF = "\\$\\{computer.jnlpmac\\}";
    private static final String NAME_REF = "\\$\\{computer.name\\}";

    @Nonnull
    private final KubernetesCloud cloud;
    @Nonnull
    private final PodTemplate t;
    @CheckForNull
    private final Label label;

    public ProvisioningCallback(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate t, @CheckForNull Label label) {
        this.cloud = cloud;
        this.t = t;
        this.label = label;
    }

    public Node call() throws Exception {
        KubernetesSlave slave = null;
        RetentionStrategy retentionStrategy = null;
        try {
            if (t.getIdleMinutes() == 0) {
                retentionStrategy = new OnceRetentionStrategy(cloud.getRetentionTimeout());
            } else {
                retentionStrategy = new CloudRetentionStrategy(t.getIdleMinutes());
            }

            final PodTemplate unwrappedTemplate = PodTemplateUtils.unwrap(cloud.getTemplate(label),
                    cloud.getDefaultsProviderTemplate(), cloud.getTemplates());
            slave = new KubernetesSlave(unwrappedTemplate, unwrappedTemplate.getName(), cloud.name,
                    unwrappedTemplate.getLabel(), retentionStrategy);

            LOGGER.log(Level.FINER, "Adding Jenkins node: {0}", slave.getNodeName());
            Jenkins.getActiveInstance().addNode(slave);

            KubernetesClient client = cloud.connect();
            Pod pod = getPodTemplate(slave, unwrappedTemplate);

            String podId = pod.getMetadata().getName();
            String namespace = Strings.isNullOrEmpty(t.getNamespace()) ? client.getNamespace() : t.getNamespace();

            LOGGER.log(Level.FINE, "Creating Pod: {0} in namespace {1}", new Object[] { podId, namespace });
            pod = client.pods().inNamespace(namespace).create(pod);
            LOGGER.log(Level.INFO, "Created Pod: {0} in namespace {1}", new Object[] { podId, namespace });

            // We need the pod to be running and connected before returning
            // otherwise this method keeps being called multiple times
            ImmutableList<String> validStates = ImmutableList.of("Running");

            int i = 0;
            int j = 100; // wait 600 seconds

            List<ContainerStatus> containerStatuses = null;

            // wait for Pod to be running
            for (; i < j; i++) {
                LOGGER.log(Level.INFO, "Waiting for Pod to be scheduled ({1}/{2}): {0}", new Object[] { podId, i, j });
                Thread.sleep(6000);
                pod = cloud.connect().pods().inNamespace(namespace).withName(podId).get();
                if (pod == null) {
                    throw new IllegalStateException("Pod no longer exists: " + podId);
                }

                containerStatuses = pod.getStatus().getContainerStatuses();
                List<ContainerStatus> terminatedContainers = new ArrayList<>();
                Boolean allContainersAreReady = true;
                for (ContainerStatus info : containerStatuses) {
                    if (info != null) {
                        if (info.getState().getWaiting() != null) {
                            // Pod is waiting for some reason
                            LOGGER.log(Level.INFO, "Container is waiting {0} [{2}]: {1}",
                                    new Object[] { podId, info.getState().getWaiting(), info.getName() });
                            // break;
                        }
                        if (info.getState().getTerminated() != null) {
                            terminatedContainers.add(info);
                        } else if (!info.getReady()) {
                            allContainersAreReady = false;
                        }
                    }
                }

                if (!terminatedContainers.isEmpty()) {
                    Map<String, Integer> errors = terminatedContainers.stream().collect(Collectors
                            .toMap(ContainerStatus::getName, (info) -> info.getState().getTerminated().getExitCode()));

                    // Print the last lines of failed containers
                    logLastLines(terminatedContainers, podId, namespace, slave, errors);
                    throw new IllegalStateException("Containers are terminated with exit codes: " + errors);
                }

                if (!allContainersAreReady) {
                    continue;
                }

                if (validStates.contains(pod.getStatus().getPhase())) {
                    break;
                }
            }
            String status = pod.getStatus().getPhase();
            if (!validStates.contains(status)) {
                throw new IllegalStateException("Container is not running after " + j + " attempts, status: " + status);
            }

            j = t.getSlaveConnectTimeout();

            // now wait for slave to be online
            for (; i < j; i++) {
                if (slave.getComputer() == null) {
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (slave.getComputer().isOnline()) {
                    break;
                }
                LOGGER.log(Level.INFO, "Waiting for slave to connect ({1}/{2}): {0}", new Object[] { podId, i, j });
                Thread.sleep(1000);
            }
            if (!slave.getComputer().isOnline()) {
                if (containerStatuses != null) {
                    logLastLines(containerStatuses, podId, namespace, slave, null);
                }
                throw new IllegalStateException("Slave is not connected after " + j + " attempts, status: " + status);
            }

            return slave;
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "Error in provisioning; slave={0}, template={1}: {2}",
                    new Object[] { slave, t, ex.getMessage() });
            if (slave != null) {
                LOGGER.log(Level.FINER, "Removing Jenkins node: {0}", slave.getNodeName());
                Jenkins.getInstance().removeNode(slave);
            }
            throw Throwables.propagate(ex);
        }
    }

     /**
      * Log the last lines of containers logs
      */
     private void logLastLines(List<ContainerStatus> containers, String podId, String namespace, KubernetesSlave slave,
             Map<String, Integer> errors) {
         for (ContainerStatus containerStatus : containers) {
             String containerName = containerStatus.getName();

             try {
                 PrettyLoggable<String, LogWatch> tailingLines = cloud.connect().pods().inNamespace(namespace)
                         .withName(podId).inContainer(containerStatus.getName()).tailingLines(30);
                 String log = tailingLines.getLog();
                 if (!StringUtils.isBlank(log)) {
                     String msg = errors != null ? String.format(" exited with error %s", errors.get(containerName))
                             : "";
                     LOGGER.log(Level.SEVERE,
                             "Error in provisioning; slave={0}, template={1}. Container {2}{3}. Logs: {4}",
                             new Object[] { slave, t, containerName, msg, tailingLines.getLog() });
                 }
             } catch (UnrecoverableKeyException | CertificateEncodingException | NoSuchAlgorithmException
                     | KeyStoreException | IOException e) {
                 LOGGER.log(Level.SEVERE, "Could not get logs for pod " + podId, e);
             }
         }
     }

    private Container createContainer(KubernetesSlave slave, ContainerTemplate containerTemplate, Collection<TemplateEnvVar> globalEnvVars, Collection<VolumeMount> volumeMounts) {
        // Last-write wins map of environment variable names to values
        HashMap<String, String> env = new HashMap<>();

        // Add some default env vars for Jenkins
        env.put("JENKINS_SECRET", slave.getComputer().getJnlpMac());
        env.put("JENKINS_NAME", slave.getComputer().getName());

        String url = cloud.getJenkinsUrlOrDie();

        env.put("JENKINS_URL", url);
        if (!StringUtils.isBlank(cloud.getJenkinsTunnel())) {
            env.put("JENKINS_TUNNEL", cloud.getJenkinsTunnel());
        }

        url = url.endsWith("/") ? url : url + "/";
        env.put("JENKINS_JNLP_URL", url + slave.getComputer().getUrl() + "slave-agent.jnlp");

        // Running on OpenShift Enterprise, security concerns force use of arbitrary user ID
        // As a result, container is running without a home set for user, resulting into using `/` for some tools,
        // and `?` for java build tools. So we force HOME to a safe location.
        env.put("HOME", containerTemplate.getWorkingDir());

        List<EnvVar> envVarsList = new ArrayList<>();

        if (globalEnvVars != null) {
            envVarsList.addAll(globalEnvVars.stream()
                    .map(TemplateEnvVar::buildEnvVar)
                    .collect(Collectors.toList()));
        }
        if (containerTemplate.getEnvVars() != null) {
            envVarsList.addAll(containerTemplate.getEnvVars().stream()
                    .map(TemplateEnvVar::buildEnvVar)
                    .collect(Collectors.toList()));
        }

        List<EnvVar> defaultEnvVars = env.entrySet().stream()
                .map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null))
                .collect(Collectors.toList());
        envVarsList.addAll(defaultEnvVars);
        EnvVar [] envVars = envVarsList.stream().toArray(EnvVar[]::new);

        List<String> arguments = Strings.isNullOrEmpty(containerTemplate.getArgs()) ? Collections.emptyList()
                : parseDockerCommand(containerTemplate.getArgs() //
                .replaceAll(JNLPMAC_REF, slave.getComputer().getJnlpMac()) //
                .replaceAll(NAME_REF, slave.getComputer().getName()));


        List<VolumeMount> containerMounts = new ArrayList<>(volumeMounts);

        ContainerPort[] ports = containerTemplate.getPorts().stream().map(entry -> entry.toPort()).toArray(size -> new ContainerPort[size]);

        if (!Strings.isNullOrEmpty(containerTemplate.getWorkingDir())
                && !PodVolume.volumeMountExists(containerTemplate.getWorkingDir(), volumeMounts)) {
            containerMounts.add(new VolumeMount(containerTemplate.getWorkingDir(), WORKSPACE_VOLUME_NAME, false, null));
        }

        ContainerLivenessProbe clp = containerTemplate.getLivenessProbe();
        Probe livenessProbe = null;
        if(clp != null && parseLivenessProbe(clp.getExecArgs()) != null) {
            livenessProbe = new ProbeBuilder()
                    .withExec(new ExecAction(parseLivenessProbe(clp.getExecArgs())))
                    .withInitialDelaySeconds(clp.getInitialDelaySeconds())
                    .withTimeoutSeconds(clp.getTimeoutSeconds())
                    .withFailureThreshold(clp.getFailureThreshold())
                    .withPeriodSeconds(clp.getPeriodSeconds())
                    .withSuccessThreshold(clp.getSuccessThreshold())
                    .build();
        }

        return new ContainerBuilder()
                .withName(substituteEnv(containerTemplate.getName()))
                .withImage(substituteEnv(containerTemplate.getImage()))
                .withImagePullPolicy(containerTemplate.isAlwaysPullImage() ? "Always" : "IfNotPresent")
                .withNewSecurityContext()
                .withPrivileged(containerTemplate.isPrivileged())
                .endSecurityContext()
                .withWorkingDir(substituteEnv(containerTemplate.getWorkingDir()))
                .withVolumeMounts(containerMounts.toArray(new VolumeMount[containerMounts.size()]))
                .addToEnv(envVars)
                .addToPorts(ports)
                .withCommand(parseDockerCommand(containerTemplate.getCommand()))
                .withArgs(arguments)
                .withLivenessProbe(livenessProbe)
                .withTty(containerTemplate.isTtyEnabled())
                .withNewResources()
                .withRequests(getResourcesMap(containerTemplate.getResourceRequestMemory(), containerTemplate.getResourceRequestCpu()))
                .withLimits(getResourcesMap(containerTemplate.getResourceLimitMemory(), containerTemplate.getResourceLimitCpu()))
                .endResources()
                .build();
    }


    private Pod getPodTemplate(KubernetesSlave slave, PodTemplate template) {
        if (template == null) {
            return null;
        }

        // Build volumes and volume mounts.
        List<Volume> volumes = new ArrayList<>();
        Map<String, VolumeMount> volumeMounts = new HashMap();

        int i = 0;
        for (final PodVolume volume : template.getVolumes()) {
            final String volumeName = "volume-" + i;
            //We need to normalize the path or we can end up in really hard to debug issues.
            final String mountPath = substituteEnv(Paths.get(volume.getMountPath()).normalize().toString());
            if (!volumeMounts.containsKey(mountPath)) {
                volumeMounts.put(mountPath, new VolumeMount(mountPath, volumeName, false, null));
                volumes.add(volume.buildVolume(volumeName));
                i++;
            }
        }

        if (template.getWorkspaceVolume() != null) {
            volumes.add(template.getWorkspaceVolume().buildVolume(WORKSPACE_VOLUME_NAME));
        } else {
            // add an empty volume to share the workspace across the pod
            volumes.add(new VolumeBuilder().withName(WORKSPACE_VOLUME_NAME).withNewEmptyDir("").build());
        }

        Map<String, Container> containers = new HashMap<>();

        for (ContainerTemplate containerTemplate : template.getContainers()) {
            containers.put(containerTemplate.getName(), createContainer(slave, containerTemplate, template.getEnvVars(), volumeMounts.values()));
        }

        if (!containers.containsKey(JNLP_NAME)) {
            ContainerTemplate containerTemplate = new ContainerTemplate(DEFAULT_JNLP_IMAGE);
            containerTemplate.setName(JNLP_NAME);
            containerTemplate.setArgs(DEFAULT_JNLP_ARGUMENTS);
            containers.put(JNLP_NAME, createContainer(slave, containerTemplate, template.getEnvVars(), volumeMounts.values()));
        }

        List<LocalObjectReference> imagePullSecrets = template.getImagePullSecrets().stream()
                .map((x) -> x.toLocalObjectReference()).collect(Collectors.toList());
        return new PodBuilder()
                .withNewMetadata()
                .withName(substituteEnv(slave.getNodeName()))
                .withLabels(cloud.getLabelsMap(template.getLabelSet()))
                .withAnnotations(getAnnotationsMap(template.getAnnotations()))
                .endMetadata()
                .withNewSpec()
                .withVolumes(volumes)
                .withServiceAccount(substituteEnv(template.getServiceAccount()))
                .withImagePullSecrets(imagePullSecrets)
                .withContainers(containers.values().toArray(new Container[containers.size()]))
                .withNodeSelector(getNodeSelectorMap(template.getNodeSelector()))
                .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    private Map<String, Quantity> getResourcesMap(String memory, String cpu) {
        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity> builder();
        String actualMemory = substituteEnv(memory);
        String actualCpu = substituteEnv(cpu);
        if (StringUtils.isNotBlank(actualMemory)) {
            Quantity memoryQuantity = new Quantity(actualMemory);
            builder.put("memory", memoryQuantity);
        }
        if (StringUtils.isNotBlank(actualCpu)) {
            Quantity cpuQuantity = new Quantity(actualCpu);
            builder.put("cpu", cpuQuantity);
        }
        return builder.build();
    }

    private Map<String, String> getAnnotationsMap(List<PodAnnotation> annotations) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
        if (annotations != null) {
            for (PodAnnotation podAnnotation : annotations) {
                builder.put(podAnnotation.getKey(), substituteEnv(podAnnotation.getValue()));
            }
        }
        return builder.build();
    }

    private Map<String, String> getNodeSelectorMap(String selectors) {
        if (Strings.isNullOrEmpty(selectors)) {
            return ImmutableMap.of();
        } else {
            ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();

            for (String selector : selectors.split(",")) {
                String[] parts = selector.split("=");
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    builder = builder.put(parts[0], substituteEnv(parts[1]));
                } else {
                    LOGGER.log(Level.WARNING, "Ignoring selector '" + selector
                            + "'. Selectors must be in the format 'label1=value1,label2=value2'.");
                }
            }
            return builder.build();
        }
    }

    /**
     * Split a command in the parts that Docker need
     *
     * @param dockerCommand
     * @return
     */
    @Restricted(NoExternalUse.class)
    List<String> parseDockerCommand(String dockerCommand) {
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
    List<String> parseLivenessProbe(String livenessProbeExec) {
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

}
