package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
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
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
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
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

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

    private static final String DEFAULT_ID = "jenkins-slave-default";
    private static final String WORKSPACE_VOLUME_NAME = "workspace-volume";

    /** label for all pods started by the plugin */
    private static final Map<String, String> POD_LABEL = ImmutableMap.of("jenkins", "slave");

    private static final String JNLPMAC_REF = "\\$\\{computer.jnlpmac\\}";
    private static final String NAME_REF = "\\$\\{computer.name\\}";

    /** Default timeout for idle workers that don't correctly indicate exit. */
    private static final int DEFAULT_RETENTION_TIMEOUT_MINUTES = 5;

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

    private transient KubernetesClient client;

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
    }

    @Deprecated
    public KubernetesCloud(String name, List<? extends PodTemplate> templates, String serverUrl, String namespace,
            String jenkinsUrl, String containerCapStr, int connectTimeout, int readTimeout, int retentionTimeout) {
        this(name);

        Preconditions.checkArgument(!StringUtils.isBlank(serverUrl));

        setServerUrl(serverUrl);
        setNamespace(namespace);
        setJenkinsUrl(jenkinsUrl);
        if (templates != null)
            this.templates.addAll(templates);
        setContainerCapStr(containerCapStr);
        setRetentionTimeout(retentionTimeout);
    }

    public int getRetentionTimeout() {
        return retentionTimeout;
    }

    @DataBoundSetter
    public void setRetentionTimeout(int retentionTimeout) {
        this.retentionTimeout = retentionTimeout;
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
        Preconditions.checkArgument(!StringUtils.isBlank(serverUrl));
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
                    client = new KubernetesFactoryAdapter(serverUrl, serverCertificate, credentialsId, skipTlsVerify)
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
        return "jenkins-" + label.getName();
    }

    private Container createContainer(KubernetesSlave slave, ContainerTemplate containerTemplate, List<VolumeMount> volumeMounts) {
        List<EnvVar> env = new ArrayList<EnvVar>(3);
        // always add some env vars
        env.add(new EnvVar("JENKINS_SECRET", slave.getComputer().getJnlpMac(), null));
        env.add(new EnvVar("JENKINS_NAME", slave.getComputer().getName(), null));
        JenkinsLocationConfiguration locationConfiguration = JenkinsLocationConfiguration.get();
        String locationConfigurationUrl = locationConfiguration != null ? locationConfiguration.getUrl() : null;
        env.add(new EnvVar("JENKINS_LOCATION_URL", locationConfigurationUrl, null));
        String url = StringUtils.isBlank(jenkinsUrl) ? locationConfigurationUrl : jenkinsUrl;
        env.add(new EnvVar("JENKINS_URL", url, null));
        if (!StringUtils.isBlank(jenkinsTunnel)) {
            env.add(new EnvVar("JENKINS_TUNNEL", jenkinsTunnel, null));
        }

        if (url == null) {
            throw new IllegalStateException("Jenkins URL is null while computing JNLP url");
        }
        url = url.endsWith("/") ? url : url + "/";
        env.add(new EnvVar("JENKINS_JNLP_URL", url + slave.getComputer().getUrl() + "slave-agent.jnlp", null));

        if (containerTemplate.getEnvVars() != null) {
            for (ContainerEnvVar containerEnvVar : containerTemplate.getEnvVars()) {
                env.add(new EnvVar(containerEnvVar.getKey(), containerEnvVar.getValue(), null));
            }
        }
        // Running on OpenShift Enterprise, security concerns force use of arbitrary user ID
        // As a result, container is running without a home set for user, resulting into using `/` for some tools,
        // and `?` for java build tools. So we force HOME to a safe location.
        env.add(new EnvVar("HOME", containerTemplate.getWorkingDir(), null));

        List<String> arguments = Strings.isNullOrEmpty(containerTemplate.getArgs()) ? Collections.emptyList()
                : parseDockerCommand(containerTemplate.getArgs() //
                        .replaceAll(JNLPMAC_REF, slave.getComputer().getJnlpMac()) //
                        .replaceAll(NAME_REF, slave.getComputer().getName()));

        return new ContainerBuilder()
                .withName(containerTemplate.getName())
                .withImage(containerTemplate.getImage())
                .withImagePullPolicy(containerTemplate.isAlwaysPullImage() ? "Always" : "IfNotPresent")
                .withNewSecurityContext()
                    .withPrivileged(containerTemplate.isPrivileged())
                .endSecurityContext()
                .withWorkingDir(containerTemplate.getWorkingDir())
                .withVolumeMounts(volumeMounts)
                .withEnv(env)
                .withCommand(parseDockerCommand(containerTemplate.getCommand()))
                .withArgs(arguments)
                .withTty(containerTemplate.isTtyEnabled())
                .withNewResources()
                    .withRequests(getResourcesMap(containerTemplate.getResourceRequestMemory(), containerTemplate.getResourceRequestCpu()))
                    .withLimits(getResourcesMap(containerTemplate.getResourceLimitMemory(), containerTemplate.getResourceLimitCpu()))
                .endResources()
                .build();
    }

    private Pod getPodTemplate(KubernetesSlave slave, Label label) {
        final PodTemplate template = getTemplate(label);
        String id = getIdForLabel(label);

        List<Container> containers = new ArrayList<Container>();
        // Build volumes and volume mounts.
        List<Volume> volumes = new ArrayList<Volume>();
        List<VolumeMount> volumeMounts = new ArrayList<VolumeMount>();
        {
            int i = 0;
            for (final PodVolumes.PodVolume volume : template.getVolumes()) {
                final String volumeName = "volume-" + i;
                volumes.add(volume.buildVolume(volumeName));
                volumeMounts.add(new VolumeMount(volume.getMountPath(), volumeName, false));
                i++;
            }
        }

        // Build image pull secrets.
        List<LocalObjectReference> imagePullSecrets = new ArrayList<LocalObjectReference>();
        if (template.getImagePullSecrets() != null) {
            for (PodImagePullSecret podImagePullSecret : template.getImagePullSecrets()) {
                imagePullSecrets.add(new LocalObjectReference(podImagePullSecret.getName()));
            }
        }

        // add an empty volume to share the workspace across the pod
        volumes.add(new VolumeBuilder().withName(WORKSPACE_VOLUME_NAME).withNewEmptyDir("").build());

        for (ContainerTemplate containerTemplate : template.getContainers()) {
            List<VolumeMount> containerMounts = new ArrayList<VolumeMount>(volumeMounts);
            if (!Strings.isNullOrEmpty(containerTemplate.getWorkingDir())
                    && !PodVolumes.volumeMountExists(containerTemplate.getWorkingDir(), volumeMounts)) {
                containerMounts.add(new VolumeMount(containerTemplate.getWorkingDir(), WORKSPACE_VOLUME_NAME, false));
            }
            containers.add(createContainer(slave, containerTemplate, containerMounts));
        }

        return new PodBuilder()
                .withNewMetadata()
                    .withName(slave.getNodeName())
                    .withLabels(getLabelsFor(id))
                    .withAnnotations(getAnnotationsMap(template.getAnnotations()))
                .endMetadata()
                .withNewSpec()
                    .withVolumes(volumes)
                    .withServiceAccount(template.getServiceAccount())
                    .withImagePullSecrets(imagePullSecrets)
                    .withContainers(containers)
                    .withNodeSelector(getNodeSelectorMap(template.getNodeSelector()))
                    .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    private Map<String, String> getLabelsFor(String id) {
        return ImmutableMap.<String, String> builder().putAll(POD_LABEL).putAll(ImmutableMap.of("name", id)).build();
    }

    private Map<String, Quantity> getResourcesMap(String memory, String cpu) {
        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity> builder();
        if (StringUtils.isNotBlank(memory)) {
            Quantity memoryQuantity = new Quantity(memory);
            builder.put("memory", memoryQuantity);
        }
        if (StringUtils.isNotBlank(cpu)) {
            Quantity cpuQuantity = new Quantity(cpu);
            builder.put("cpu", cpuQuantity);
        }
        return builder.build();
    }

    private Map<String, String> getAnnotationsMap(List<PodAnnotation> annotations) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
        if (annotations != null) {
            for (PodAnnotation podAnnotation : annotations) {
                builder.put(podAnnotation.getKey(), podAnnotation.getValue());
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
                    builder = builder.put(parts[0], parts[1]);
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
            commands.add(m.group(1).replace("\"", ""));
        }
        return commands;
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(final Label label, final int excessWorkload) {
        try {

            LOGGER.log(Level.INFO, "Excess workload after pending Spot instances: " + excessWorkload);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

            final PodTemplate t = getTemplate(label);

            for (int i = 1; i <= excessWorkload; i++) {
                if (!addProvisionedSlave(t, label)) {
                    break;
                }

                r.add(new NodeProvisioner.PlannedNode(t.getDisplayName(), Computer.threadPoolForRemoting
                        .submit(new ProvisioningCallback(this, t, label)), 1));
            }
            return r;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", e);
            return Collections.emptyList();
        }
    }

    private class ProvisioningCallback implements Callable<Node> {
        private final KubernetesCloud cloud;
        private final PodTemplate t;
        private final Label label;

        public ProvisioningCallback(KubernetesCloud cloud, PodTemplate t, Label label) {
            this.cloud = cloud;
            this.t = t;
            this.label = label;
        }

        public Node call() throws Exception {
            KubernetesSlave slave = null;
            try {

                slave = new KubernetesSlave(t, getIdForLabel(label), cloud, label);
                Jenkins.getActiveInstance().addNode(slave);

                Pod pod = getPodTemplate(slave, label);
                // Why the hell doesn't createPod return a Pod object ?
                pod = connect().pods().inNamespace(namespace).create(pod);

                String podId = pod.getMetadata().getName();
                LOGGER.log(Level.INFO, "Created Pod: {0}", podId);

                // We need the pod to be running and connected before returning
                // otherwise this method keeps being called multiple times
                ImmutableList<String> validStates = ImmutableList.of("Running");

                int i = 0;
                int j = 600; // wait 600 seconds

                // wait for Pod to be running
                for (; i < j; i++) {
                    LOGGER.log(Level.INFO, "Waiting for Pod to be scheduled ({1}/{2}): {0}", new Object[] {podId, i, j});
                    pod = connect().pods().inNamespace(namespace).withName(podId).get();
                    if (pod == null) {
                        throw new IllegalStateException("Pod no longer exists: " + podId);
                    }

                    List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
                    List<ContainerStatus> terminatedContainers = new ArrayList<>();
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
                            }
                        }
                    }
                    if (!terminatedContainers.isEmpty()) {
                        Map<String, Integer> errors = terminatedContainers.stream().collect(Collectors.toMap(
                                ContainerStatus::getName, (info) -> info.getState().getTerminated().getExitCode()));
                        throw new IllegalStateException("Containers are terminated with exit codes: " + errors);
                    }

                    if (validStates.contains(pod.getStatus().getPhase())) {
                        break;
                    }

                    Thread.sleep(1000);
                }
                String status = pod.getStatus().getPhase();
                if (!validStates.contains(status)) {
                    throw new IllegalStateException("Container is not running after " + j + " attempts: " + status);
                }

                // now wait for slave to be online
                for (; i < j; i++) {
                    if (slave.getComputer() == null) {
                        throw new IllegalStateException("Node was deleted, computer is null");
                    }
                    if (slave.getComputer().isOnline()) {
                        break;
                    }
                    LOGGER.log(Level.INFO, "Waiting for slave to connect ({1}/{2}): {0}", new Object[] { podId,
                            i, j });
                    Thread.sleep(1000);
                }
                if (!slave.getComputer().isOnline()) {
                    throw new IllegalStateException("Slave is not connected after " + j + " attempts: " + status);
                }

                return slave;
            } catch (Throwable ex) {
                LOGGER.log(Level.SEVERE, "Error in provisioning; slave={0}, template={1}", new Object[] { slave, t });
                ex.printStackTrace();
                throw Throwables.propagate(ex);
            }
        }
    }

    /**
     * Check not too many already running.
     *
     */
    private boolean addProvisionedSlave(PodTemplate template, Label label) throws Exception {
        if (containerCap == 0) {
            return true;
        }

        KubernetesClient client = connect();
        PodList slaveList = client.pods().inNamespace(namespace).withLabels(POD_LABEL).list();
        String idForLabel = getIdForLabel(label);
        PodList namedList = client.pods().inNamespace(namespace).withLabel("name", idForLabel).list();

        if (containerCap < slaveList.getItems().size()) {
            LOGGER.log(Level.INFO, "Total container cap of {0} reached, not provisioning: {1} running in namespace {2}",
                    new Object[] { containerCap, slaveList.getItems().size(), namespace });
            return false;
        }

        if (template.getInstanceCap() < namedList.getItems().size()) {
            LOGGER.log(Level.INFO,
                    "Template instance cap of {0} reached for template {1}, not provisioning: {2} running in namespace {3} with label {4}",
                    new Object[] { template.getInstanceCap(), template.getName(), slaveList.getItems().size(),
                            namespace, idForLabel });
            return false; // maxed out
        }
        return true;
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    /**
     * Gets {@link PodTemplate} that has the matching {@link Label}.
     * @param label label to look for in templates
     * @return the template
     */
    public PodTemplate getTemplate(Label label) {
        for (PodTemplate t : templates) {
            if (label == null || label.matches(t.getLabelSet())) {
                return t;
            }
        }
        return null;
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
                                               @QueryParameter String namespace) throws Exception {

            KubernetesClient client = new KubernetesFactoryAdapter(serverUrl.toExternalForm(),
                    Util.fixEmpty(serverCertificate), Util.fixEmpty(credentialsId), skipTlsVerify)
                    .createClient();

            client.pods().inNamespace(namespace).list();
            return FormValidation.ok("Connection successful");
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
        return String.format("KubernetesCloud name: %s serverUrl: %s", name, serverUrl);
    }

    private Object readResolve() {
        return this;
    }

}
