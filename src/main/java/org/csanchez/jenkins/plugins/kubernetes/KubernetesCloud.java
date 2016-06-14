package org.csanchez.jenkins.plugins.kubernetes;

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
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.transform.ImmutableASTTransformation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

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

import javax.annotation.CheckForNull;

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

    /** label for all pods started by the plugin */
    private static final Map<String, String> POD_LABEL = ImmutableMap.of("jenkins", "slave");

    private static final String CONTAINER_NAME = "slave";

    /** Default timeout for idle workers that don't correctly indicate exit. */
    private static final int DEFAULT_RETENTION_TIMEOUT_MINUTES = 5;

    private final List<PodTemplate> templates;
    private final String serverUrl;
    @CheckForNull
    private String serverCertificate;

    private boolean skipTlsVerify;

    private String namespace;
    private final String jenkinsUrl;
    @CheckForNull
    private String jenkinsTunnel;
    @CheckForNull
    private String credentialsId;
    private final int containerCap;
    private final int retentionTimeout;

    private transient KubernetesClient client;

    @DataBoundConstructor
    public KubernetesCloud(String name, List<? extends PodTemplate> templates, String serverUrl, String namespace,
            String jenkinsUrl, String containerCapStr, int connectTimeout, int readTimeout, int retentionTimeout) {
        super(name);

        Preconditions.checkArgument(!StringUtils.isBlank(serverUrl));

        this.serverUrl = serverUrl;
        this.namespace = namespace;
        this.jenkinsUrl = jenkinsUrl;
        if (templates != null)
            this.templates = new ArrayList<PodTemplate>(templates);
        else
            this.templates = new ArrayList<PodTemplate>();

        if (containerCapStr.equals("")) {
            this.containerCap = Integer.MAX_VALUE;
        } else {
            this.containerCap = Integer.parseInt(containerCapStr);
        }

        if (retentionTimeout > 0) {
            this.retentionTimeout = retentionTimeout;
        } else {
            this.retentionTimeout = DEFAULT_RETENTION_TIMEOUT_MINUTES;
        }
    }

    public int getRetentionTimeout() {
        return retentionTimeout;
    }

    public List<PodTemplate> getTemplates() {
        return templates;
    }

    public String getServerUrl() {
        return serverUrl;
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

    public String getNamespace() {
        return namespace;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
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

    public String getContainerCapStr() {
        if (containerCap == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(containerCap);
        }
    }

    /**
     * Connects to Docker.
     *
     * @return Docker client.
     * @throws CertificateEncodingException
     */
    public KubernetesClient connect() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
            IOException, CertificateEncodingException {

        LOGGER.log(Level.FINE, "Building connection to Kubernetes host " + name + " URL " + serverUrl);

        if (client == null) {
            synchronized (this) {
                if (client != null)
                    return client;

                client = new KubernetesFactoryAdapter(serverUrl, serverCertificate, credentialsId, skipTlsVerify)
                        .createClient();
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

    private Pod getPodTemplate(KubernetesSlave slave, Label label) {
        final PodTemplate template = getTemplate(label);
        String id = getIdForLabel(label);
        List<EnvVar> env = new ArrayList<EnvVar>(3);
        // always add some env vars
        env.add(new EnvVar("JENKINS_SECRET", slave.getComputer().getJnlpMac(), null));
        env.add(new EnvVar("JENKINS_LOCATION_URL", JenkinsLocationConfiguration.get().getUrl(), null));
        String url = StringUtils.isBlank(jenkinsUrl) ? JenkinsLocationConfiguration.get().getUrl() : jenkinsUrl;
        env.add(new EnvVar("JENKINS_URL", url, null));
        if (!StringUtils.isBlank(jenkinsTunnel)) {
            env.add(new EnvVar("JENKINS_TUNNEL", jenkinsTunnel, null));
        }
        url = url.endsWith("/") ? url : url + "/";
        env.add(new EnvVar("JENKINS_JNLP_URL", url + slave.getComputer().getUrl() + "slave-agent.jnlp", null));

        if(template.getEnvVars()!=null) {
            for (PodEnvVar podEnvVar :template.getEnvVars()) {
                env.add(new EnvVar(podEnvVar.getKey(), podEnvVar.getValue(), null));
            }
        }

        // Running on OpenShift Enterprise, security concerns force use of arbitrary user ID
        // As a result, container is running without a home set for user, resulting into using `/` for some tools,
        // and `?` for java build tools. So we force HOME to a safe location.
        env.add(new EnvVar("HOME", template.getRemoteFs(), null));


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

        return new PodBuilder()
                .withNewMetadata()
                    .withName(slave.getNodeName())
                    .withLabels(getLabelsFor(id))
                .endMetadata()
                .withNewSpec()
                    .withVolumes(volumes)
                    .addNewContainer()
                        .withName(CONTAINER_NAME)
                        .withImage(template.getImage())
                        .withImagePullPolicy(template.isAlwaysPullImage() ? "Always" : "IfNotPresent")
                        .withNewSecurityContext()
                            .withPrivileged(template.isPrivileged())
                            .endSecurityContext()
                        .withWorkingDir(template.getRemoteFs())
                        .withVolumeMounts(volumeMounts)
                        .withEnv(env)
                        .withCommand(parseDockerCommand(template.getCommand()))
                        .withNewResources()
                            .withRequests(getResourcesMap(template.getResourceRequestMemory(), template.getResourceRequestCpu()))
                            .withLimits(getResourcesMap(template.getResourceLimitMemory(), template.getResourceLimitCpu()))
                            .endResources()
                        .addToArgs(slave.getComputer().getJnlpMac())
                        .addToArgs(slave.getComputer().getName())
                .endContainer()
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
                Jenkins.getInstance().addNode(slave);

                Pod pod = getPodTemplate(slave, label);
                // Why the hell doesn't createPod return a Pod object ?
                pod = connect().pods().inNamespace(namespace).create(pod);

                String podId = pod.getMetadata().getName();
                LOGGER.log(Level.INFO, "Created Pod: {0}", podId);

                // We need the pod to be running and connected before returning
                // otherwise this method keeps being called multiple times
                ImmutableList<String> validStates = ImmutableList.of("Running");

                int i = 0;
                int j = 100; // wait 600 seconds

                // wait for Pod to be running
                for (; i < j; i++) {
                    LOGGER.log(Level.INFO, "Waiting for Pod to be scheduled ({1}/{2}): {0}", new Object[] {podId, i, j});
                    Thread.sleep(6000);
                    pod = connect().pods().inNamespace(namespace).withName(podId).get();
                    if (pod == null) {
                        throw new IllegalStateException("Pod no longer exists: " + podId);
                    }
                    ContainerStatus info = getContainerStatus(pod, CONTAINER_NAME);
                    if (info != null) {
                        if (info.getState().getWaiting() != null) {
                            // Pod is waiting for some reason
                            LOGGER.log(Level.INFO, "Pod is waiting {0}: {1}",
                                    new Object[] { podId, info.getState().getWaiting() });
                            // break;
                        }
                        if (info.getState().getTerminated() != null) {
                            throw new IllegalStateException("Pod is terminated. Exit code: "
                                    + info.getState().getTerminated().getExitCode());
                        }
                    }
                    if (validStates.contains(pod.getStatus().getPhase())) {
                        break;
                    }
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

    private ContainerStatus getContainerStatus(Pod pod, String containerName) {

        for (ContainerStatus status : pod.getStatus().getContainerStatuses()) {
            if (status.getName().equals(containerName)) return status;
        }
        return null;
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
        PodList namedList = client.pods().inNamespace(namespace).withLabel("name", getIdForLabel(label)).list();


        if (containerCap < slaveList.getItems().size()) {
            LOGGER.log(Level.INFO, "Total container cap of " + containerCap + " reached, not provisioning.");
            return false;
        }

        if (template.getInstanceCap() < namedList.getItems().size()) {
            LOGGER.log(Level.INFO, "Template instance cap of " + template.getInstanceCap() + " reached for template "
                    + template.getImage() + ", not provisioning.");
            return false; // maxed out
        }
        return true;
    }

    @Override
    public boolean canProvision(Label label) {
        return getTemplate(label) != null;
    }

    public PodTemplate getTemplate(String template) {
        for (PodTemplate t : templates) {
            if (t.getImage().equals(template)) {
                return t;
            }
        }
        return null;
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
        return String.format("KubernetesCloud name: %n serverUrl: %n", name, serverUrl);
    }

    private Object readResolve() {
        if (namespace == null) namespace = "jenkins-slave";
        return this;
    }

}
