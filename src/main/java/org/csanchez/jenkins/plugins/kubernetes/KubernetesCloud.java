package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.labels.LabelAtom;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateStepExecution;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.HOME;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_JNLP_URL;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_LOCATION_URL;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_NAME;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_SECRET;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_TUNNEL;
import static org.csanchez.jenkins.plugins.kubernetes.PodEnvVar.EnvironmentVariableNames.JENKINS_URL;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.substituteEnv;
import static org.csanchez.jenkins.plugins.kubernetes.SlaveTimeLimitedTaskRunner.slaveOperationMaxAttempts;

/**
 * Kubernetes cloud provider.
 *
 * Starts slaves in a Kubernetes cluster using defined Docker templates for each label.
 *
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesCloud extends Cloud {

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());
    private static final Pattern SPLIT_IN_SPACES = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    private static final String DEFAULT_ID = "jenkins/slave-default";
    private static final String WORKSPACE_VOLUME_NAME = "workspace-volume";

    public static final String JNLP_NAME = "jnlp";
    private static final String DEFAULT_JNLP_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private static final String DEFAULT_JNLP_IMAGE = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultImage", "jenkinsci/jnlp-slave:alpine");

    /** label for all pods started by the plugin */
    private static final Map<String, String> POD_LABEL = ImmutableMap.of("jenkins", "slave");

    private static final String JNLPMAC_REF = "\\$\\{computer.jnlpmac\\}";
    private static final String NAME_REF = "\\$\\{computer.name\\}";

    /** Default timeout for idle workers that don't correctly indicate exit. */
    private static final int DEFAULT_RETENTION_TIMEOUT_MINUTES = 5;

    private String defaultsProviderTemplate;

    private List<PodTemplate> templates = new ArrayList<PodTemplate>();
    private String serverUrl;
    @CheckForNull
    private String serverCertificate;

    private boolean skipTlsVerify;

    private String namespace;
    private String jenkinsUrl;
    @CheckForNull
    private String jenkinsTunnel;
    @CheckForNull
    private String credentialsId;
    private int containerCap = Integer.MAX_VALUE;
    private int retentionTimeout = DEFAULT_RETENTION_TIMEOUT_MINUTES;
    private int connectTimeout;
    private int readTimeout;

    private transient KubernetesClient client;

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    @Deprecated
    public KubernetesCloud(String name, List<? extends PodTemplate> templates, String serverUrl, String namespace,
                           String jenkinsUrl, String containerCapStr, int connectTimeout, int readTimeout, int retentionTimeout) {
        this(name);

        Preconditions.checkArgument(!isBlank(serverUrl));

        setServerUrl(serverUrl);
        setNamespace(namespace);
        setJenkinsUrl(jenkinsUrl);
        if (templates != null) {
            this.templates.addAll(templates);
        }
        setContainerCapStr(containerCapStr);
        setRetentionTimeout(retentionTimeout);
        setConnectTimeout(connectTimeout);
        setReadTimeout(readTimeout);

    }

    public int getRetentionTimeout() {
        return retentionTimeout;
    }

    @DataBoundSetter
    public void setRetentionTimeout(int retentionTimeout) {
        this.retentionTimeout = retentionTimeout;
    }

    public String getDefaultsProviderTemplate() {
        return defaultsProviderTemplate;
    }

    @DataBoundSetter
    public void setDefaultsProviderTemplate(String defaultsProviderTemplate) {
        this.defaultsProviderTemplate = defaultsProviderTemplate;
    }

    public List<PodTemplate> getTemplates() {
        return templates;
    }

    @DataBoundSetter
    public void setTemplates(@Nonnull List<PodTemplate> templates) {
        this.templates = templates;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(@Nonnull String serverUrl) {
        Preconditions.checkArgument(!isBlank(serverUrl));
        this.serverUrl = serverUrl;
    }

    public String getServerCertificate() {
        return serverCertificate;
    }

    @DataBoundSetter
    public void setServerCertificate(String serverCertificate) {
        this.serverCertificate = Util.fixEmpty(serverCertificate);
    }

    public boolean isSkipTlsVerify() {
        return skipTlsVerify;
    }

    @DataBoundSetter
    public void setSkipTlsVerify(boolean skipTlsVerify) {
        this.skipTlsVerify = skipTlsVerify;
    }

    @CheckForNull
    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = Util.fixEmpty(namespace);
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
    }

    public String getJenkinsTunnel() {
        return jenkinsTunnel;
    }

    @DataBoundSetter
    public void setJenkinsTunnel(String jenkinsTunnel) {
        this.jenkinsTunnel = Util.fixEmpty(jenkinsTunnel);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public int getContainerCap() {
        return containerCap;
    }

    @DataBoundSetter
    public void setContainerCapStr(String containerCapStr) {
        if (containerCapStr.equals("")) {
            this.containerCap = Integer.MAX_VALUE;
        } else {
            this.containerCap = Integer.parseInt(containerCapStr);
        }
    }

    public String getContainerCapStr() {
        if (containerCap == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(containerCap);
        }
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Connects to Kubernetes.
     *
     * @return Kubernetes client.
     */
    @SuppressFBWarnings({ "IS2_INCONSISTENT_SYNC", "DC_DOUBLECHECK" })
    public KubernetesClient connect() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
            IOException, CertificateEncodingException {

        LOGGER.log(Level.FINE, "Building connection to Kubernetes host " + name + " URL " + serverUrl);

        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new KubernetesFactoryAdapter(serverUrl, namespace, serverCertificate, credentialsId, skipTlsVerify, connectTimeout, readTimeout)
                            .createClient();
                }
            }
        }
        return client;

    }

    private String getIdForLabel(Label label) {
        if (label == null) {
            return DEFAULT_ID;
        }
        return "jenkins/" + label.getName();
    }


    @VisibleForTesting
    Container createContainer(SlaveInfo slaveInfo, ContainerTemplate containerTemplate, Collection<PodEnvVar> globalEnvVars, Collection<VolumeMount> volumeMounts) {
        // Last-write wins map of environment variable names to values
        HashMap<String, String> env = new HashMap<>();

        JenkinsLocationConfiguration locationConfiguration = getJenkinsLocationConfiguration();
        String locationConfigurationUrl = locationConfiguration != null ? locationConfiguration.getUrl() : null;
        String url = isBlank(jenkinsUrl) ? locationConfigurationUrl : jenkinsUrl;

        if (url == null) {
            throw new IllegalStateException("Jenkins URL is null while computing JNLP url");
        }

        env.put(JENKINS_LOCATION_URL, locationConfigurationUrl);
        env.put(JENKINS_URL, url);
        if (!isBlank(jenkinsTunnel)) {
            env.put(JENKINS_TUNNEL, jenkinsTunnel);
        }

        url = url.endsWith("/") ? url : url + "/";
        // Computer data is unknown at this stage if slave self-registers
        if (slaveInfo.isComputerDataPresent()) {
            // Add some default env vars for Jenkins
            env.put(JENKINS_SECRET, slaveInfo.getComputerJnlpMac());
            env.put(JENKINS_NAME, slaveInfo.getComputerName());
            env.put(JENKINS_JNLP_URL, url + slaveInfo.getComputerUrl() + "slave-agent.jnlp");
        }

        // Running on OpenShift Enterprise, security concerns force use of arbitrary user ID
        // As a result, container is running without a home set for user, resulting into using `/` for some tools,
        // and `?` for java build tools. So we force HOME to a safe location.
        env.put(HOME, containerTemplate.getWorkingDir());

        if (globalEnvVars != null) {
            for (PodEnvVar podEnvVar : globalEnvVars) {
                env.put(podEnvVar.getKey(), substituteEnv(podEnvVar.getValue()));
            }
        }

        if (containerTemplate.getEnvVars() != null) {
            for (ContainerEnvVar containerEnvVar : containerTemplate.getEnvVars()) {
                env.put(containerEnvVar.getKey(), substituteEnv(containerEnvVar.getValue()));
            }
        }

        // Convert our env map to an array
        EnvVar[] envVars = env.entrySet().stream()
                .map(entry -> new EnvVar(entry.getKey(), entry.getValue(), null))
                .toArray(size -> new EnvVar[size]);

        List<String> arguments;
        String containerTemplateArgs = containerTemplate.getArgs();
        if (Strings.isNullOrEmpty(containerTemplateArgs)) {
            arguments = Collections.emptyList();
        } else {
            if (slaveInfo.isComputerDataPresent()) {
                containerTemplateArgs = containerTemplateArgs
                        .replaceAll(JNLPMAC_REF, slaveInfo.getComputerJnlpMac())
                        .replaceAll(NAME_REF, slaveInfo.getComputerName());
            }
            arguments = parseDockerCommand(containerTemplateArgs);
        }

        List<VolumeMount> containerMounts = new ArrayList<>(volumeMounts);

        if (!Strings.isNullOrEmpty(containerTemplate.getWorkingDir())
                && !PodVolume.volumeMountExists(containerTemplate.getWorkingDir(), volumeMounts)) {
            containerMounts.add(new VolumeMount(containerTemplate.getWorkingDir(), WORKSPACE_VOLUME_NAME, false, null));
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
                .withCommand(parseDockerCommand(containerTemplate.getCommand()))
                .withArgs(arguments)
                .withTty(containerTemplate.isTtyEnabled())
                .withNewResources()
                .withRequests(getResourcesMap(containerTemplate.getResourceRequestMemory(), containerTemplate.getResourceRequestCpu()))
                .withLimits(getResourcesMap(containerTemplate.getResourceLimitMemory(), containerTemplate.getResourceLimitCpu()))
                .endResources()
                .build();
    }

    @VisibleForTesting
    JenkinsLocationConfiguration getJenkinsLocationConfiguration() {
        return JenkinsLocationConfiguration.get();
    }


    @VisibleForTesting
    Pod getPodTemplate(SlaveInfo slaveInfo, @CheckForNull Label label) {
        final PodTemplate template = unwrapTemplateFromLabel(label);
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

        Map<String, Container> containers = getNameToContainerMap(slaveInfo, template, volumeMounts);

        List<LocalObjectReference> imagePullSecrets = template.getImagePullSecrets().stream()
                .map((x) -> x.toLocalObjectReference()).collect(Collectors.toList());
        return new PodBuilder()
                .withNewMetadata()
                .withName(substituteEnv(slaveInfo.getNodeName()))
                .withLabels(getLabelsMap(template.getLabelSet()))
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

    @VisibleForTesting
    Map<String, Container> getNameToContainerMap(SlaveInfo slaveInfo, PodTemplate template, Map<String, VolumeMount> volumeMounts) {
        Map<String, Container> containers = new HashMap<>();

        // Does one of the containers represent a Jenkins agent?
        int agentContainerCount = 0;
        List<PodEnvVar> envVars = template.getEnvVars();
        for (ContainerTemplate containerTemplate : template.getContainers()) {
            containers.put(containerTemplate.getName(), createContainer(slaveInfo, containerTemplate, envVars, volumeMounts.values()));
            if (isJenkinsAgentTemplate(containerTemplate)) {
                agentContainerCount++;
            }
        }

        // If no agents are present in template, we need a default one to make pod available as agent in Jenkins
        if (agentContainerCount == 0) {
            ContainerTemplate containerTemplate = new ContainerTemplate(JNLP_NAME, DEFAULT_JNLP_IMAGE);
            containerTemplate.setArgs(DEFAULT_JNLP_ARGUMENTS);
            containers.put(JNLP_NAME, createContainer(slaveInfo, containerTemplate, envVars, volumeMounts.values()));
        } else if (agentContainerCount > 1) {
            throw new IllegalStateException(format("Template contains at least %d agent container images - should be only one", agentContainerCount));
        }
        return containers;
    }

    @VisibleForTesting
    PodTemplate unwrapTemplateFromLabel(@CheckForNull Label label) {
        return PodTemplateUtils.unwrap(getTemplate(label), defaultsProviderTemplate, templates);
    }

    @VisibleForTesting
    boolean isJenkinsAgentTemplate(ContainerTemplate containerTemplate) {
        return JNLP_NAME.equals(containerTemplate.getName()) || containerTemplate.isSlaveImage();
    }

    private Map<String, String> getLabelsMap(Set<LabelAtom> labelSet) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
        builder.putAll(POD_LABEL);
        if (!labelSet.isEmpty()) {
            for (LabelAtom label: labelSet) {
                builder.put(getIdForLabel(label), "true");
            }
        }
        return builder.build();
    }

    private Map<String, Quantity> getResourcesMap(String memory, String cpu) {
        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity> builder();
        String actualMemory = substituteEnv(memory, null);
        String actualCpu = substituteEnv(cpu, null);
        if (isNotBlank(actualMemory)) {
            Quantity memoryQuantity = new Quantity(actualMemory);
            builder.put("memory", memoryQuantity);
        }
        if (isNotBlank(actualCpu)) {
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

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(@CheckForNull final Label label, final int excessWorkload) {
        try {
            LOGGER.log(Level.INFO, "Excess workload after pending Spot instances: " + excessWorkload);
            return getPlannedNodes(label, excessWorkload, getMatchingTemplates(label));
        } catch (KubernetesClientException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause instanceof SocketTimeoutException) {
                    LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes: {0}",
                            cause.getMessage());
                } else {
                    LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", cause);
                }
            } else {
                LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", e);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", e);
        }
        return Collections.emptyList();
    }

    @VisibleForTesting
    Collection<NodeProvisioner.PlannedNode> getPlannedNodes(@CheckForNull Label label, int excessWorkload, List<PodTemplate> templates) throws Exception {
        boolean shouldAddAtLeastOneSlave = false;

        List<NodeProvisioner.PlannedNode> r = new ArrayList<>();
        for (PodTemplate t: templates) {
            for (int i = 1; i <= excessWorkload; i++) {
                if (!addProvisionedSlave(t, label)) {
                    break;
                }
                shouldAddAtLeastOneSlave = true;
                Callable<Node> provisioningCallback = t.hasSelfRegisteringSlave()
                        ? new SelfRegisteringSlaveCallback(this, t, label)
                        : new SimpleProvisioningCallback(this, t, label);
                r.add(getPlannedNode(t, provisioningCallback));
            }
            if (shouldAddAtLeastOneSlave) {
                // Already found a matching template
                return r;
            }
        }
        return r;
    }

    @VisibleForTesting
    NodeProvisioner.PlannedNode getPlannedNode(PodTemplate t, Callable<Node> provisioningCallback) {
        return new NodeProvisioner.PlannedNode(t.getDisplayName(),
                Computer.threadPoolForRemoting.submit(provisioningCallback), 1);
    }

    @VisibleForTesting
    public class SimpleProvisioningCallback implements Callable<Node> {

        @VisibleForTesting
        static final String NODE_WAS_DELETED_COMPUTER_IS_NULL_MSG = "Node was deleted, computer is null";

        @Nonnull
        protected final KubernetesCloud cloud;

        @Nonnull
        protected final PodTemplate t;

        @CheckForNull
        protected final Label label;

        public SimpleProvisioningCallback(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate t, @CheckForNull Label label) {
            this.cloud = cloud;
            this.t = t;
            this.label = label;
        }

        /**
         * Log the last lines of containers logs
         */
        private void logLastLines(List<ContainerStatus> containers, Pod pod, Map<String, Integer> errors) {
            logLastLines(containers, getPodId(pod), getPodNameSpace(pod), errors);
        }

        private void logLastLines(List<ContainerStatus> containers, String podId, String namespace, Map<String, Integer> errors) {
            for (ContainerStatus containerStatus : containers) {
                String containerName = containerStatus.getName();

                try {
                    PrettyLoggable<String, LogWatch> tailingLines = connect().pods().inNamespace(namespace).withName(podId)
                            .inContainer(containerStatus.getName()).tailingLines(30);
                    String log = tailingLines.getLog();
                    if (!isBlank(log)) {
                        String msg = errors != null ? format(" exited with error %s", errors.get(containerName))
                                : "";
                        LOGGER.log(Level.SEVERE,
                                "Error in provisioning; slave={0}, template={1}. Container {2}{3}. Logs: {4}",
                                new Object[] { podId, t, containerName, msg, tailingLines.getLog() });
                    }
                } catch (UnrecoverableKeyException | CertificateEncodingException | NoSuchAlgorithmException
                        | KeyStoreException | IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not get logs for pod " + podId, e);
                }
            }
        }

        public Node call() throws Exception {
            Slave slave = null;
            try {
                RetentionStrategy retentionStrategy = getRetentionStrategy();
                slave = new KubernetesSlave(t, t.getName(), cloud.name, t.getLabel(), retentionStrategy);
                LOGGER.log(Level.FINER, "Adding Jenkins node: {0}", slave.getNodeName());
                jenkins().addNode(slave);

                SlaveComputer slaveComputer = slave.getComputer();
                SlaveInfo slaveInfo =
                        new SlaveInfo(slave.getNodeName(), slaveComputer.getName(), slaveComputer.getUrl(), slaveComputer.getJnlpMac());
                return spinUpSlaveFromPod(slaveInfo);
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Error in provisioning; slave={0}, template={1}", new Object[] { slave, t });
                if (slave != null) {
                    LOGGER.log(Level.FINER, "Removing Jenkins node: {0}", slave.getNodeName());
                    jenkins().removeNode(slave);
                }
                throw Throwables.propagate(ex);
            }
        }

        Node spinUpSlaveFromPod(SlaveInfo slaveInfo) throws Exception {
            KubernetesClient client = connect();
            Pod podTemplate = getPodTemplate(slaveInfo, label);
            LOGGER.log(Level.FINE, "Pod template for pod creation: {0}", podTemplate);

            String namespace = getNamespace(client);
            Pod pod = createPod(client, podTemplate, namespace);
            String podId = getPodId(pod);
            LOGGER.log(Level.INFO, "Created Pod: {0}", podId);

            // We need the pod to be running and connected before returning
            // otherwise this method keeps being called multiple times
            ImmutableList<String> validStates = ImmutableList.of("Running");

            LOGGER.log(Level.FINER, "Starting to wait for Pod {0} to be in status {1}", new Object[] { podId, validStates });
            waitForPodStatus(podId, namespace, validStates);
            LOGGER.log(Level.INFO, "Pod {0} is ready, waiting for slave to come online", new Object[] { podId });

            // now wait for slave to be online
            return waitForSlaveToComeOnline(pod, namespace);
        }

        Pod createPod(KubernetesClient client, Pod podTemplate, String namespace) {
            return client.pods().inNamespace(namespace).create(podTemplate);
        }

        String getNamespace(KubernetesClient client) {
            return Strings.isNullOrEmpty(t.getNamespace())
                    ? client.getNamespace()
                    : t.getNamespace();
        }

        String getPodId(Pod pod) {
            return pod.getMetadata().getName();
        }

        String getPodNameSpace(Pod pod) {
            return pod.getMetadata().getNamespace();
        }

        @VisibleForTesting
        Slave waitForSlaveToComeOnline(Pod pod, String namespace) throws Exception {
            int attemptsToMake = slaveOperationMaxAttempts(t.getSlaveConnectTimeout());
            String podId = getPodId(pod);
            Slave slave = (Slave) jenkins().getNode(podId);

            try {
                SlaveOperationDetails slaveOperationDetails = SlaveTimeLimitedTaskRunner.performUntilTimeout(attemptNumber -> {
                    if (slave == null || slave.getComputer() == null) {
                        throw new IllegalStateException(NODE_WAS_DELETED_COMPUTER_IS_NULL_MSG);
                    }
                    if (slave.getComputer().isOnline()) {
                        LOGGER.log(Level.INFO, "Slave from pod {0} is online", new Object[]{podId});
                        return Optional.of(new SlaveOperationDetails(slave, null));
                    }
                    LOGGER.log(Level.INFO, "Waiting for slave to connect ({1}/{2}): {0}",
                            new Object[]{podId, attemptNumber, attemptsToMake});
                    return Optional.empty();
                }, attemptsToMake);
                return slaveOperationDetails.getSlave();
            } catch (SlaveOperationTimeoutException e) {
                logLastSlaveContainerLines(pod);
                throw failureToConnectOnlineSlave(pod, attemptsToMake);
            }
        }

        /**
         * To throw when the slave is not connected or not online after the timeout.
         * @param pod   pod containing slave container
         * @param slaveConnectTimeoutInSeconds
         * @return exception to throw
         */
        IllegalStateException failureToConnectOnlineSlave(Pod pod, int slaveConnectTimeoutInSeconds) {
            // Refresh the pod before reporting its state (may be stale)
            pod = getPodByNamespaceAndPodId(getPodNameSpace(pod), getPodId(pod));
            return new IllegalStateException(format("Slave is not connected and online after %d seconds, status: %s",
                    slaveConnectTimeoutInSeconds, getPodStatus(pod)));
        }

        void logLastSlaveContainerLines(Pod pod) {
            List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
            if (containerStatuses != null) {
                logLastLines(containerStatuses, pod, null);
            }
        }

        boolean isSlaveComputerOnline(Slave slave) {
            return slave != null && slave.getComputer() != null && slave.getComputer().isOnline();
        }

        protected Jenkins jenkins() {
            return Jenkins.getInstance();
        }

        void waitForPodStatus(String podId, String namespace, ImmutableList<String> validStates) throws Exception {
            int startChecks = 0;
            Pod pod = null;
            boolean inValidState = false;
            // wait for 600 seconds for Pod to be running
            for (int i = 0, j = 100; i < j; i++) {
                startChecks++;
                LOGGER.log(Level.INFO, "Waiting for Pod to be scheduled ({1}/{2}): {0}", new Object[] {podId, i, j});
                Thread.sleep(6000);
                pod = getPodByNamespaceAndPodId(namespace, podId);
                if (pod == null) {
                    throw new IllegalStateException("Pod no longer exists: " + podId);
                }

                List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
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
                    Map<String, Integer> errors = terminatedContainers.stream().collect(Collectors.toMap(
                            ContainerStatus::getName, (info) -> info.getState().getTerminated().getExitCode()));
                    // Print the last lines of failed containers
                    logLastLines(terminatedContainers, podId, namespace, errors);
                    throw new IllegalStateException("Containers are terminated with exit codes: " + errors);
                }

                if (!allContainersAreReady) {
                    continue;
                }

                String podStatus = getPodStatus(pod);
                LOGGER.log(Level.INFO, "Pod status is {0}", podStatus);
                if (validStates.contains(podStatus)) {
                    inValidState = true;
                    break;
                }
            }
            if (!inValidState) {
                throw new IllegalStateException(format("Container is not running after %d attempts, status: %s",
                        startChecks, getPodStatus(pod)));
            }
        }

        String getPodStatus(Pod pod) {
            return pod.getStatus().getPhase();
        }

        Pod getPodByNamespaceAndPodId(String namespace, String podId) {
            try {
                return connect().pods().inNamespace(namespace).withName(podId).get();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

        RetentionStrategy getRetentionStrategy() {
            RetentionStrategy retentionStrategy;
            if (t.getIdleMinutes() == 0) {
                retentionStrategy = new OnceRetentionStrategy(cloud.getRetentionTimeout());
            } else {
                retentionStrategy = new CloudRetentionStrategy(t.getIdleMinutes());
            }
            return retentionStrategy;
        }
    }

    /**
     * For agents that have means to register themselves in Jenkins (e.g., Swarm agents)
     * The callback currently assumes that slave name starts with the pod name (as slave agent can potentially
     * have a custom name)
     */
    @VisibleForTesting
    class SelfRegisteringSlaveCallback extends SimpleProvisioningCallback {

        private static final String SELF_REG_SLAVE_LABEL = "k8s_self_registered_slave";

        public SelfRegisteringSlaveCallback(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate t, @CheckForNull Label label) {
            super(cloud, t, label);
        }

        @Override
        public Node call() throws Exception {
            String slaveName = KubernetesSlave.getSlaveName(t);
            SlaveInfo slaveInfo = new SlaveInfo(slaveName);
            try {
                return spinUpSlaveFromPod(slaveInfo);
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Error in provisioning; slaveName={0}, template={1}",
                        new Object[] { slaveInfo.getNodeName(), t });
                throw Throwables.propagate(ex);
            }
        }

        @VisibleForTesting
        @Override
        Slave waitForSlaveToComeOnline(Pod pod, String namespace) throws Exception {
            String podId = getPodId(pod);
            LOGGER.log(Level.INFO, "Waiting for slave from pod {0} (namespace {1}) to connect", new Object[] { podId, namespace });

            // wait for slave to be connected
            SlaveOperationDetails slaveConnectDetails = waitForSlaveToConnect(podId, namespace);
            Slave foundSlaveNode = slaveConnectDetails.getSlave();
            String slaveName = slaveConnectDetails.getSlaveName();

            // now wait for slave to be online
            LOGGER.log(Level.INFO, "Waiting for slave from pod {0} (namespace {1}) to come online", new Object[] { podId, namespace });
            int onlineWaitTimeoutInSeconds = slaveOperationMaxAttempts(t.getSlaveConnectTimeout() - slaveConnectDetails.getSecondsSpent());

            SlaveOperationDetails slaveOperationDetails;
            try {
                slaveOperationDetails = SlaveTimeLimitedTaskRunner.performUntilTimeout(attemptNumber -> {
                    if (isSlaveComputerOnline(foundSlaveNode)) {
                        addTemplateLabels(foundSlaveNode, t.getLabel(), SELF_REG_SLAVE_LABEL);
                        // Slave is self-registering here, but we want our retention strategy to apply
                        applyTemplateRetentionStrategy(foundSlaveNode, namespace, podId);
                        return Optional.of(new SlaveOperationDetails(foundSlaveNode, slaveName));
                    }
                    LOGGER.log(Level.INFO, "Waiting for slave to connect ({1}/{2}): {0}",
                            new Object[]{slaveName, attemptNumber, onlineWaitTimeoutInSeconds});
                    return Optional.empty();
                }, onlineWaitTimeoutInSeconds);
            } catch (SlaveOperationTimeoutException e) {
                logLastSlaveContainerLines(pod);
                throw failureToConnectOnlineSlave(pod, onlineWaitTimeoutInSeconds);
            }

            return slaveOperationDetails.getSlave();
        }

        private void applyTemplateRetentionStrategy(Slave slave, String namespace, String podId) {
            slave.setRetentionStrategy(new SelfRegisteredSlaveRetentionStrategy(cloud.name, namespace, podId, t.getIdleMinutes()));
            try {
                // Needed to make retention strategy effective
                jenkins().updateNode(slave);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        private void addTemplateLabels(Slave slave, String... labels) {
            try {
                for (String label : labels) {
                    slave.setLabelString(slave.getLabelString() + " " + label);
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            // This getter magically activates the labels set above
            slave.getAssignedLabels();
        }

        @VisibleForTesting
        SlaveOperationDetails waitForSlaveToConnect(String podId, String namespace) throws InterruptedException {
            int slaveConnectTimeoutInSeconds = slaveOperationMaxAttempts(t.getSlaveConnectTimeout());
            try {
                return SlaveTimeLimitedTaskRunner.performUntilTimeout(attemptNumber -> {
                    Optional<Node> optionalKubeSlave = findSelfRegisteredSlaveByPodId(podId);
                    return optionalKubeSlave
                            .map(kubeSlave -> Optional.of(new SlaveOperationDetails((Slave)kubeSlave, kubeSlave.getDisplayName())))
                            .orElseGet(() -> {
                                LOGGER.log(Level.INFO, "pod {0} is not online yet", podId);
                                return Optional.empty();
                            });
                }, slaveConnectTimeoutInSeconds);
            } catch (SlaveOperationTimeoutException e) {
                // Slave still not connected? Let's fail here
                Pod pod = getPodByNamespaceAndPodId(namespace, podId);
                logLastSlaveContainerLines(pod);
                throw failureToConnectOnlineSlave(pod, slaveConnectTimeoutInSeconds);
            }
        }

        @VisibleForTesting
        Optional<Node> findSelfRegisteredSlaveByPodId(String podId) {
            LOGGER.log(Level.INFO, "Trying to find slave with name equal to pod ID \"{0}\"", new Object[] { podId });
            // Self-registering slave name should be equal to Pod ID
            return Optional.ofNullable(jenkins().getNode(podId));
        }
    }

    /**
     * Check not too many already running.
     *
     */
    @VisibleForTesting
    boolean addProvisionedSlave(@Nonnull PodTemplate template, @CheckForNull Label label) throws Exception {
        if (containerCap == 0) {
            return true;
        }

        KubernetesClient client = connect();
        PodList slaveList = client.pods().inNamespace(template.getNamespace()).withLabels(POD_LABEL).list();
        List<Pod> slaveListItems = slaveList.getItems();

        Map<String, String> labelsMap = getLabelsMap(template.getLabelSet());
        PodList namedList = client.pods().inNamespace(template.getNamespace()).withLabels(labelsMap).list();
        List<Pod> namedListItems = namedList.getItems();

        if (slaveListItems != null && containerCap <= slaveListItems.size()) {
            LOGGER.log(Level.INFO, "Total container cap of {0} reached, not provisioning: {1} running in namespace {2}",
                    new Object[] { containerCap, slaveListItems.size(), client.getNamespace() });
            return false;
        }

        if (namedListItems != null && slaveListItems != null && template.getInstanceCap() <= namedListItems.size()) {
            LOGGER.log(Level.INFO,
                    "Template instance cap of {0} reached for template {1}, not provisioning: {2} running in namespace {3} with label {4}",
                    new Object[] { template.getInstanceCap(), template.getName(), slaveListItems.size(),
                            client.getNamespace(), label == null ? "" : label.toString() });
            return false; // maxed out
        }
        return true;
    }

    @Override
    public boolean canProvision(@CheckForNull Label label) {
        return getTemplate(label) != null;
    }

    /**
     * Gets {@link PodTemplate} that has the matching {@link Label}.
     * @param label label to look for in templates
     * @return the template
     */
    public PodTemplate getTemplate(@CheckForNull Label label) {
        return PodTemplateUtils.getTemplateByLabel(label, templates);
    }

    /**
     * Gets all PodTemplates that have the matching {@link Label}.
     * @param label label to look for in templates
     * @return list of matching templates
     */
    public ArrayList<PodTemplate> getMatchingTemplates(@CheckForNull Label label) {
        ArrayList<PodTemplate> podList = new ArrayList<PodTemplate>();
        for (PodTemplate t : templates) {
            if (label == null || label.matches(t.getLabelSet())) {
                podList.add(t);
            }
        }
        return podList;
    }

    /**
     * Add a new template to the cloud
     * @param t docker template
     */
    public void addTemplate(PodTemplate t) {
        this.templates.add(t);
        // t.parent = this;
    }

    /**
     * Remove a
     *
     * @param t docker template
     */
    public void removeTemplate(PodTemplate t) {
        this.templates.remove(t);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Kubernetes";
        }

        public FormValidation doTestConnection(@QueryParameter URL serverUrl, @QueryParameter String credentialsId,
                                               @QueryParameter String serverCertificate,
                                               @QueryParameter boolean skipTlsVerify,
                                               @QueryParameter String namespace,
                                               @QueryParameter int connectionTimeout,
                                               @QueryParameter int readTimeout) throws Exception {

            try {
                KubernetesClient client = new KubernetesFactoryAdapter(serverUrl.toExternalForm(), namespace,
                        Util.fixEmpty(serverCertificate), Util.fixEmpty(credentialsId), skipTlsVerify,
                        connectionTimeout, readTimeout).createClient();

                client.pods().list();
                return FormValidation.ok("Connection successful");
            } catch (KubernetesClientException e) {
                return FormValidation.error("Error connecting to %s: %s", serverUrl,
                        e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            } catch (Exception e) {
                return FormValidation.error("Error connecting to %s: %s", serverUrl, e.getMessage());
            }
        }

        public ListBoxModel doFillCredentialsIdItems(@QueryParameter URL serverUrl) {
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(TokenProducer.class),
                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class)
                            ),
                            CredentialsProvider.lookupCredentials(StandardCredentials.class,
                                    Jenkins.getInstance(),
                                    ACL.SYSTEM,
                                    serverUrl != null ? URIRequirementBuilder.fromUri(serverUrl.toExternalForm()).build()
                                            : Collections.EMPTY_LIST
                            ));

        }

    }

    @Override
    public String toString() {
        return format("KubernetesCloud name: %s serverUrl: %s", name, serverUrl);
    }

    private Object readResolve() {
        return this;
    }

}
