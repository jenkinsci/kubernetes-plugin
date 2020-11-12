package org.csanchez.jenkins.plugins.kubernetes;

import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import com.google.common.annotations.VisibleForTesting;
import hudson.Main;
import hudson.model.ItemGroup;
import hudson.model.Node;
import hudson.util.XStream2;
import jenkins.metrics.api.Metrics;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateMap;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Default;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuth;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Label;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.VersionInfo;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.csanchez.jenkins.plugins.kubernetes.MetricNames.metricNameForLabel;

import jenkins.websocket.WebSockets;

/**
 * Kubernetes cloud provider.
 *
 * Starts agents in a Kubernetes cluster using defined Docker templates for each label.
 *
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesCloud extends Cloud {
    public static final int DEFAULT_MAX_REQUESTS_PER_HOST = 32;
    public static final Integer DEFAULT_WAIT_FOR_POD_SEC = 600;

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());

    public static final String JNLP_NAME = "jnlp";
    /** label for all pods started by the plugin */
    @Deprecated
    public static final Map<String, String> DEFAULT_POD_LABELS = ImmutableMap.of("jenkins", "slave");

    /** Default timeout for idle workers that don't correctly indicate exit. */
    public static final int DEFAULT_RETENTION_TIMEOUT_MINUTES = 5;

    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 15;

    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;

    private String defaultsProviderTemplate;

    @Nonnull
    private List<PodTemplate> templates = new ArrayList<>();
    private String serverUrl;
    private boolean useJenkinsProxy;
    @CheckForNull
    private String serverCertificate;

    private boolean skipTlsVerify;
    private boolean addMasterProxyEnvVars;

    private boolean capOnlyOnAlivePods;

    private String namespace;
    private boolean webSocket;
    private boolean directConnection = false;
    private String jenkinsUrl;
    @CheckForNull
    private String jenkinsTunnel;
    @CheckForNull
    private String credentialsId;
    private Integer containerCap;
    private int retentionTimeout = DEFAULT_RETENTION_TIMEOUT_MINUTES;
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT_SECONDS;
    private int readTimeout = DEFAULT_READ_TIMEOUT_SECONDS;
    /** @deprecated Stored as a list of PodLabels */
    @Deprecated
    private transient Map<String, String> labels;
    private List<PodLabel> podLabels = new ArrayList<>();
    private boolean usageRestricted;

    private int maxRequestsPerHost;

    // Integer to differentiate null from 0
    private Integer waitForPodSec = DEFAULT_WAIT_FOR_POD_SEC;

    @CheckForNull
    private PodRetention podRetention = PodRetention.getKubernetesCloudDefault();

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
        setMaxRequestsPerHost(DEFAULT_MAX_REQUESTS_PER_HOST);
    }

    /**
     * Copy constructor.
     * Allows to create copies of the original kubernetes cloud. Since it's a singleton
     * by design, this method also allows specifying a new name.
     * @param name Name of the cloud to be created
     * @param source Source Kubernetes cloud implementation
     * @since 0.13
     */
    public KubernetesCloud(@NonNull String name, @NonNull KubernetesCloud source) {
        super(name);
        XStream2 xs = new XStream2();
        xs.omitField(Cloud.class, "name");
        xs.omitField(KubernetesCloud.class, "templates"); // TODO PodTemplate and fields needs to implement equals
        xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xs.toXML(source))), this);
        this.templates.addAll(source.templates);
    }

    @Deprecated
    public KubernetesCloud(String name, List<? extends PodTemplate> templates, String serverUrl, String namespace,
            String jenkinsUrl, String containerCapStr, int connectTimeout, int readTimeout, int retentionTimeout) {
        this(name);

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

    public boolean isUseJenkinsProxy() { return useJenkinsProxy; }
    @DataBoundSetter
    public void setUseJenkinsProxy(boolean useJenkinsProxy) { this.useJenkinsProxy = useJenkinsProxy; }

    public boolean isUsageRestricted() {
        return usageRestricted;
    }

    @DataBoundSetter
    public void setUsageRestricted(boolean usageRestricted) {
        this.usageRestricted = usageRestricted;
    }
    
    public int getRetentionTimeout() {
        return retentionTimeout;
    }

    @DataBoundSetter
    public void setRetentionTimeout(int retentionTimeout) {
        this.retentionTimeout = Math.max(DEFAULT_RETENTION_TIMEOUT_MINUTES, retentionTimeout);
    }

    public String getDefaultsProviderTemplate() {
        return defaultsProviderTemplate;
    }

    @DataBoundSetter
    public void setDefaultsProviderTemplate(String defaultsProviderTemplate) {
        this.defaultsProviderTemplate = defaultsProviderTemplate;
    }

    @Nonnull
    public List<PodTemplate> getTemplates() {
        return templates;
    }

    /**
     * Returns all pod templates for this cloud including the dynamic ones.
     * @return all pod templates for this cloud including the dynamic ones.
     */
    @Nonnull
    public List<PodTemplate> getAllTemplates() {
        return PodTemplateSource.getAll(this);
    }

    @DataBoundSetter
    public void setTemplates(@Nonnull List<PodTemplate> templates) {
        this.templates = new ArrayList<>(templates);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(@Nonnull String serverUrl) {
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
    
    public boolean isAddMasterProxyEnvVars() {
    	return this.addMasterProxyEnvVars;
    }
    
    @DataBoundSetter
    public void setAddMasterProxyEnvVars(boolean addMasterProxyEnvVars) {
    	this.addMasterProxyEnvVars = addMasterProxyEnvVars;
    }

    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = Util.fixEmpty(namespace);
    }

    @CheckForNull
    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    @DataBoundSetter
    @Deprecated
    public void setCapOnlyOnAlivePods(boolean capOnlyOnAlivePods) {
        this.capOnlyOnAlivePods = capOnlyOnAlivePods;
    }

    @Deprecated
    public boolean isCapOnlyOnAlivePods() {
        return capOnlyOnAlivePods;
    }

    /**
     * Returns Jenkins URL to be used by agents launched by this cloud. Always ends with a trailing slash.
     *
     * Uses in order:
     * * cloud configuration
     * * environment variable <b>KUBERNETES_JENKINS_URL</b>
     * * Jenkins Location URL
     *
     * @return Jenkins URL to be used by agents launched by this cloud. Always ends with a trailing slash.
     * @throws IllegalStateException if no Jenkins URL could be computed.
     */
    @Nonnull
    public String getJenkinsUrlOrDie() {
        String url = getJenkinsUrlOrNull();
        if (url == null) {
            throw new IllegalStateException("Jenkins URL for Kubernetes is null");
        }
        return url;
    }

    /**
     * Returns Jenkins URL to be used by agents launched by this cloud. Always ends with a trailing slash.
     *
     * Uses in order:
     * * cloud configuration
     * * environment variable <b>KUBERNETES_JENKINS_URL</b>
     * * Jenkins Location URL
     *
     * @return Jenkins URL to be used by agents launched by this cloud. Always ends with a trailing slash.
     *         Null if no Jenkins URL could be computed.
     */
    @CheckForNull
    public String getJenkinsUrlOrNull() {
        JenkinsLocationConfiguration locationConfiguration = JenkinsLocationConfiguration.get();
        String url = StringUtils.defaultIfBlank(
                getJenkinsUrl(),
                StringUtils.defaultIfBlank(
                        System.getProperty("KUBERNETES_JENKINS_URL",System.getenv("KUBERNETES_JENKINS_URL")),
                        locationConfiguration.getUrl()
                )
        );
        if (url == null) {
            return null;
        }
        url = url.endsWith("/") ? url : url + "/";
        return url;
    }

    public boolean isWebSocket() {
        return webSocket;
    }

    @DataBoundSetter
    public void setWebSocket(boolean webSocket) {
        this.webSocket = webSocket;
    }

    public boolean isDirectConnection() {
        return directConnection;
    }

    @DataBoundSetter
    public void setDirectConnection(boolean directConnection) {
        this.directConnection = directConnection;
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = Util.fixEmptyAndTrim(jenkinsUrl);
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
        return containerCap != null ? containerCap : Integer.MAX_VALUE;
    }

    @DataBoundSetter
    public void setContainerCapStr(String containerCapStr) {
        setContainerCap(containerCapStr.equals("") ? null : Integer.parseInt(containerCapStr));
    }

    public void setContainerCap(Integer containerCap) {
        this.containerCap = (containerCap != null && containerCap > 0) ? containerCap : null;
    }

    public String getContainerCapStr() {
        // null, serialized Integer.MAX_VALUE, or 0 means no limit
        return (containerCap == null || containerCap == Integer.MAX_VALUE || containerCap == 0) ? "" : String.valueOf(containerCap);
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    @DataBoundSetter
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = Math.max(DEFAULT_READ_TIMEOUT_SECONDS, readTimeout);
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Labels for all pods started by the plugin
     * @return immutable map of pod labels
     * @deprecated use {@link #getPodLabels()}
     */
    @Deprecated
    public Map<String, String> getLabels() {
        return getPodLabelsMap();
    }

    /**
     * Set pod labels
     *
     * @param labels pod labels
     * @deprecated use {@link #setPodLabels(List)}
     */
    @Deprecated
    public void setLabels(Map<String, String> labels) {
        setPodLabels(labels != null ? PodLabel.fromMap(labels) : Collections.emptyList());
    }

    /**
     * Labels for all pods started by the plugin
     */
    @NonNull
    public List<PodLabel> getPodLabels() {
        return podLabels == null || podLabels.isEmpty() ? PodLabel.fromMap(DEFAULT_POD_LABELS) : podLabels;
    }

    /**
     * Set Pod labels  for all pods started by the plugin.
     */
    @DataBoundSetter
    public void setPodLabels(@CheckForNull List<PodLabel> labels) {
        this.podLabels = new ArrayList<>();
        if (labels != null) {
            this.podLabels.addAll(labels);
        }
    }

    /**
     * Map of labels to add to all pods started by the plugin
     * @return immutable map of pod labels
     */
    Map<String, String> getPodLabelsMap() {
        return PodLabel.toMap(getPodLabels());
    }

    @DataBoundSetter
    public void setMaxRequestsPerHostStr(String maxRequestsPerHostStr) {
        try  {
            setMaxRequestsPerHost(Integer.parseInt(maxRequestsPerHostStr));
        } catch (NumberFormatException e) {
            setMaxRequestsPerHost(DEFAULT_MAX_REQUESTS_PER_HOST);
        }
    }

    @DataBoundSetter
    public void setMaxRequestsPerHost(int maxRequestsPerHost) {
        if (maxRequestsPerHost < 0) {
            this.maxRequestsPerHost = DEFAULT_MAX_REQUESTS_PER_HOST;
        } else {
            this.maxRequestsPerHost = maxRequestsPerHost;
        }
    }

    public String getMaxRequestsPerHostStr() {
        return String.valueOf(maxRequestsPerHost);
    }

    public int getMaxRequestsPerHost() {
        return maxRequestsPerHost;
    }

    @DataBoundSetter
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = Math.max(DEFAULT_CONNECT_TIMEOUT_SECONDS, connectTimeout);
    }

    /**
     * Gets the global pod retention policy for the plugin.
     */
    public PodRetention getPodRetention() {
        return this.podRetention;
    }
    
    /**
     * Set the global pod retention policy for the plugin.
     * 
     * @param podRetention the pod retention policy for the plugin.
     */
    @DataBoundSetter
    public void setPodRetention(PodRetention podRetention) {
        if (podRetention == null || podRetention instanceof Default) {
            podRetention = PodRetention.getKubernetesCloudDefault();
        }
        this.podRetention = podRetention;
    }

    /**
     * Connects to Kubernetes.
     *
     * @return Kubernetes client.
     */
    @SuppressFBWarnings({ "IS2_INCONSISTENT_SYNC", "DC_DOUBLECHECK" })
    public KubernetesClient connect() throws KubernetesAuthException, IOException {

        LOGGER.log(Level.FINEST, "Building connection to Kubernetes {0} URL {1} namespace {2}",
                new String[] { getDisplayName(), serverUrl, namespace });
        KubernetesClient client = KubernetesClientProvider.createClient(this);

        LOGGER.log(Level.FINE, "Connected to Kubernetes {0} URL {1} namespace {2}", new String[] { getDisplayName(), client.getMasterUrl().toString(), namespace });
        return client;
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(@NonNull final Cloud.CloudState state, final int excessWorkload) {
        try {
            Metrics.metricRegistry().meter(metricNameForLabel(state.getLabel())).mark(excessWorkload);
            Label label = state.getLabel();
            int plannedCapacity = state.getAdditionalPlannedCapacity(); // Planned nodes, will be launched on the next round of NodeProvisioner
            Set<String> allInProvisioning = InProvisioning.getAllInProvisioning(label); // Nodes being launched
            LOGGER.log(Level.FINE, () -> "In provisioning : " + allInProvisioning);
            int toBeProvisioned = Math.max(0, excessWorkload - allInProvisioning.size());
            Set<Node> nodes = getNodes(label);
            int currentExecutorsCount = (int) nodes.stream().filter(KubernetesSlave.class::isInstance).map(Node::getNumExecutors).count();
            int launchingExecutorsCount = (int) nodes.stream().filter(KubernetesSlave.class::isInstance).filter(n -> n.toComputer() != null && ((KubernetesComputer)n.toComputer()).isLaunching()).map(Node::getNumExecutors).count();
            LOGGER.log(Level.INFO, "Label \"{0}\" excess workload: {1}," +
                    " executors: {2}," +
                    " plannedCapacity: {3}," +
                            " launching: {4}",
                    new Object[] {label, toBeProvisioned, currentExecutorsCount, plannedCapacity, launchingExecutorsCount});
            List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<>();
            List<Pod> pods = getPodsWithLabels(namespace, getPodLabelsMap());
            if (pods != null) {
                // Count planned and executors not yet launching (e.g. everything that is not in kubernetes yet)
                int plannedCount = plannedCapacity + currentExecutorsCount - launchingExecutorsCount;
                int remainingGlobalSlots = getRemainingGlobalSlots(pods, plannedCount);
                if (remainingGlobalSlots > 0) {
                    LOGGER.fine(() -> "Global slots left for " + name + ": " + remainingGlobalSlots);
                    for (PodTemplate podTemplate : getTemplatesFor(label)) {
                        LOGGER.log(Level.FINE, "Template for label \"{0}\": {1}", new Object[]{label, podTemplate.getName()});
                        // check overall concurrency limit using the default label(s) on all templates
                        int remainingPodTemplateSlots = getRemainingPodTemplateSlots(podTemplate, pods, plannedCount);
                        LOGGER.log(remainingPodTemplateSlots > 0 ? Level.FINE : Level.INFO, "Slots left for template \"{0}\" in \"{1}\": {2}", new Object[]{podTemplate.getName(), name, remainingPodTemplateSlots});
                        int provisioningLimit = Math.min(remainingGlobalSlots, remainingPodTemplateSlots);
                        while (plannedNodes.size() < Math.min(provisioningLimit, toBeProvisioned)) {
                            plannedNodes.add(PlannedNodeBuilderFactory.createInstance().cloud(this).template(podTemplate).label(label).build());
                        }
                        if (!plannedNodes.isEmpty()) {
                            // Return early when a matching template was found and nodes were planned
                            LOGGER.log(Level.FINEST, "Planned {0} Kubernetes agents with template \"{1}\"", new Object[]{plannedNodes.size(), podTemplate.getName()});
                            Metrics.metricRegistry().counter(MetricNames.PROVISION_NODES).inc(plannedNodes.size());
                            if (plannedNodes.size() == provisioningLimit && plannedNodes.size() < toBeProvisioned) {
                                Metrics.metricRegistry().counter(MetricNames.REACHED_POD_CAP).inc();
                            }

                            return plannedNodes;
                        }
                    }
                } else {
                    LOGGER.log(Level.INFO, "No slot left for provisioning (global limit)");
                    Metrics.metricRegistry().counter(MetricNames.REACHED_GLOBAL_CAP).inc();
                }
            }
            Metrics.metricRegistry().counter(MetricNames.PROVISION_NODES).inc(plannedNodes.size());
            return plannedNodes;
        } catch (KubernetesClientException e) {
            Metrics.metricRegistry().counter(MetricNames.PROVISION_FAILED).inc();
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException || cause instanceof UnknownHostException) {
                LOGGER.log(Level.WARNING, "Failed to connect to Kubernetes at {0}: {1}",
                        new String[] { serverUrl, cause.getMessage() });
            } else {
                LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes",
                        cause != null ? cause : e);
            }
        } catch (ConnectException e) {
            LOGGER.log(Level.WARNING, "Failed to connect to Kubernetes at {0}", serverUrl);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", e);
        }
        return Collections.emptyList();
    }

    private Set<Node> getNodes(@CheckForNull Label label) {
        if (label != null) {
            return label.getNodes();
        } else {
            return Jenkins.get().getNodes().stream()
                    .filter(n -> n.getLabelString() == null)
                    .collect(Collectors.toSet());
        }
    }

    @VisibleForTesting
    int getRemainingPodTemplateSlots(PodTemplate template, List<Pod> pods, int plannedCount) {
        List<Pod> filteredPodList = pods.stream()
                .filter(pod -> pod.getMetadata().getLabels().entrySet().containsAll(template.getLabelsMap().entrySet()))
                .collect(Collectors.toList());
        return Math.max(0, template.getInstanceCap() - filteredPodList.size() - plannedCount);
    }

    @VisibleForTesting
    int getRemainingGlobalSlots(List<Pod> pods, int plannedCount) {
        // FIXME plannedCount only includes current requested label
        return Math.max(0, getContainerCap() - pods.size() - plannedCount);
    }

    /**
     * Query for running or pending pods
     */
    @CheckForNull
    private List<Pod> getPodsWithLabels(String namespace, Map<String, String> labels) throws IOException, KubernetesAuthException {
        KubernetesClient client = connect();
        PodList podList = client.pods()
                .inNamespace(StringUtils.defaultIfEmpty(namespace,client.getNamespace()))
                .withLabels(labels)
                .list();
        // JENKINS-53370 check for nulls
        if (podList != null && podList.getItems() != null) {
            return podList.getItems().stream() //
                    .filter(x -> x.getStatus().getPhase().toLowerCase().matches("(running|pending)"))
                    .collect(Collectors.toList());
        }
        return null;
    }

    @Override
    public boolean canProvision(@NonNull Cloud.CloudState state) {
        return getTemplate(state.getLabel()) != null;
    }

    /**
     * Gets {@link PodTemplate} that has the matching {@link Label}.
     * @param label label to look for in templates
     * @return the template
     */
    public PodTemplate getTemplate(@CheckForNull Label label) {
        return PodTemplateUtils.getTemplateByLabel(label, getAllTemplates());
    }

    @CheckForNull
    public PodTemplate getTemplateById(@Nonnull String id) {
        return getAllTemplates().stream().filter(t -> id.equals(t.getId())).findFirst().orElse(null);
    }

    /**
     * Unwraps the given pod template.
     * @param podTemplate the pod template to unwrap.
     * @return the unwrapped pod template
     */
    public PodTemplate getUnwrappedTemplate(PodTemplate podTemplate) {
        return PodTemplateUtils.unwrap(podTemplate, getDefaultsProviderTemplate(), getAllTemplates());
    }

    /**
     * Gets all PodTemplates that have the matching {@link Label}.
     * @param label label to look for in templates
     * @return list of matching templates
     * @deprecated Use {@link #getTemplatesFor(Label)} instead.
     */
    @Deprecated
    public ArrayList<PodTemplate> getMatchingTemplates(@CheckForNull Label label) {
        return new ArrayList<>(getTemplatesFor(label));
    }

    /**
     * Gets all PodTemplates that have the matching {@link Label}.
     * @param label label to look for in templates
     * @return list of matching templates
     */
    public List<PodTemplate> getTemplatesFor(@CheckForNull Label label) {
        return PodTemplateFilter.applyAll(this, getAllTemplates(), label);
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

    /**
     * Add a dynamic pod template. Won't be displayed in UI, and persisted separately from the cloud instance.
     * @param t the template to add
     */
    public void addDynamicTemplate(PodTemplate t) {
        PodTemplateMap.get().addTemplate(this, t);
    }

    /**
     * Remove a dynamic pod template.
     * @param t the template to remove
     */
    public void removeDynamicTemplate(PodTemplate t) {
        PodTemplateMap.get().removeTemplate(this, t);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KubernetesCloud that = (KubernetesCloud) o;
        return skipTlsVerify == that.skipTlsVerify &&
                addMasterProxyEnvVars == that.addMasterProxyEnvVars &&
                capOnlyOnAlivePods == that.capOnlyOnAlivePods &&
                Objects.equals(containerCap, that.containerCap) &&
                retentionTimeout == that.retentionTimeout &&
                connectTimeout == that.connectTimeout &&
                readTimeout == that.readTimeout &&
                usageRestricted == that.usageRestricted &&
                maxRequestsPerHost == that.maxRequestsPerHost &&
                Objects.equals(defaultsProviderTemplate, that.defaultsProviderTemplate) &&
                templates.equals(that.templates) &&
                Objects.equals(serverUrl, that.serverUrl) &&
                Objects.equals(serverCertificate, that.serverCertificate) &&
                Objects.equals(namespace, that.namespace) &&
                Objects.equals(jenkinsUrl, that.jenkinsUrl) &&
                Objects.equals(jenkinsTunnel, that.jenkinsTunnel) &&
                Objects.equals(credentialsId, that.credentialsId) &&
                Objects.equals(podLabels, that.podLabels) &&
                Objects.equals(podRetention, that.podRetention) &&
                Objects.equals(waitForPodSec, that.waitForPodSec) &&
                useJenkinsProxy==that.useJenkinsProxy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultsProviderTemplate, templates, serverUrl, serverCertificate, skipTlsVerify,
                addMasterProxyEnvVars, capOnlyOnAlivePods, namespace, jenkinsUrl, jenkinsTunnel, credentialsId,
                containerCap, retentionTimeout, connectTimeout, readTimeout, podLabels, usageRestricted,
                maxRequestsPerHost, podRetention, useJenkinsProxy);
    }

    public Integer getWaitForPodSec() {
        return waitForPodSec;
    }

    @DataBoundSetter
    public void setWaitForPodSec(Integer waitForPodSec) {
        this.waitForPodSec = waitForPodSec;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        @Override
        public String getDisplayName() {
            return "Kubernetes";
        }

        @Initializer(before = InitMilestone.PLUGINS_STARTED)
        public static void addAliases() {
            Jenkins.XSTREAM2.addCompatibilityAlias(
                    "org.csanchez.jenkins.plugins.kubernetes.OpenShiftBearerTokenCredentialImpl",
                    org.jenkinsci.plugins.kubernetes.credentials.OpenShiftBearerTokenCredentialImpl.class);
            Jenkins.XSTREAM2.addCompatibilityAlias(
                    "org.csanchez.jenkins.plugins.kubernetes.OpenShiftTokenCredentialImpl",
                    StringCredentialsImpl.class);
            Jenkins.XSTREAM2.addCompatibilityAlias("org.csanchez.jenkins.plugins.kubernetes.ServiceAccountCredential",
                    org.jenkinsci.plugins.kubernetes.credentials.FileSystemServiceAccountCredential.class);
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doTestConnection(@QueryParameter String name, @QueryParameter String serverUrl, @QueryParameter String credentialsId,
                                               @QueryParameter String serverCertificate,
                                               @QueryParameter boolean skipTlsVerify,
                                               @QueryParameter String namespace,
                                               @QueryParameter int connectionTimeout,
                                               @QueryParameter int readTimeout) throws Exception {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (StringUtils.isBlank(name))
                return FormValidation.error("name is required");

            try (KubernetesClient client = new KubernetesFactoryAdapter(serverUrl, namespace,
                        Util.fixEmpty(serverCertificate), Util.fixEmpty(credentialsId), skipTlsVerify,
                        connectionTimeout, readTimeout).createClient()) {
                    // test listing pods
                    client.pods().list();
                VersionInfo version = client.getVersion();
                return FormValidation.ok("Connected to Kubernetes " + version.getMajor() + "." + version.getMinor());
            } catch (KubernetesClientException e) {
                LOGGER.log(Level.FINE, String.format("Error testing connection %s", serverUrl), e);
                return FormValidation.error("Error testing connection %s: %s", serverUrl, e.getCause() == null
                        ? e.getMessage()
                        : String.format("%s: %s", e.getCause().getClass().getName(), e.getCause().getMessage()));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, String.format("Error testing connection %s", serverUrl), e);
                return FormValidation.error("Error testing connection %s: %s", serverUrl, e.getMessage());
            }
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String serverUrl) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            result.includeMatchingAs(
                ACL.SYSTEM,
                context,
                StandardCredentials.class,
                serverUrl != null ? URIRequirementBuilder.fromUri(serverUrl).build()
                            : Collections.EMPTY_LIST,
                CredentialsMatchers.anyOf(
                    AuthenticationTokens.matcher(KubernetesAuth.class)
                )
            );
            return result;
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckMaxRequestsPerHostStr(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.validatePositiveInteger(value);
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckConnectTimeout(@QueryParameter String value) {
            return FormValidation.validateIntegerInRange(value, DEFAULT_CONNECT_TIMEOUT_SECONDS, Integer.MAX_VALUE);
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckReadTimeout(@QueryParameter String value) {
            return FormValidation.validateIntegerInRange(value, DEFAULT_READ_TIMEOUT_SECONDS, Integer.MAX_VALUE);
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckRetentionTimeout(@QueryParameter String value) {
            return FormValidation.validateIntegerInRange(value, DEFAULT_RETENTION_TIMEOUT_MINUTES, Integer.MAX_VALUE);
        }

        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckDirectConnection(@QueryParameter boolean value, @QueryParameter String jenkinsUrl, @QueryParameter boolean webSocket) throws IOException, ServletException {
            int slaveAgentPort = Jenkins.get().getSlaveAgentPort();
            if (slaveAgentPort == -1 && !webSocket) {
                return FormValidation.warning("'TCP port for inbound agents' is disabled in Global Security settings. Connecting Kubernetes agents will not work without this or WebSocket mode!");
            }

            if(value) {
                if (webSocket) {
                    return FormValidation.error("Direct connection and WebSocket mode are mutually exclusive");
                }
                if(!isEmpty(jenkinsUrl)) return FormValidation.warning("No need to configure Jenkins URL when direct connection is enabled");

                if(slaveAgentPort == 0) return FormValidation.warning(
                        "A random 'TCP port for inbound agents' is configured in Global Security settings. In 'direct connection' mode agents will not be able to reconnect to a restarted master with random port!");
            } else {
                if (isEmpty(jenkinsUrl)) {
                    String url = StringUtils.defaultIfBlank(System.getProperty("KUBERNETES_JENKINS_URL", System.getenv("KUBERNETES_JENKINS_URL")), JenkinsLocationConfiguration.get().getUrl());
                    if (url != null) {
                        return FormValidation.ok("Will connect using " + url);
                    } else {
                        return FormValidation.warning("Configure either Direct Connection or Jenkins URL");
                    }
                }
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckJenkinsUrl(@QueryParameter String value, @QueryParameter boolean directConnection) throws IOException, ServletException {
            try {
                if(!isEmpty(value)) new URL(value);
            } catch (MalformedURLException e) {
                return FormValidation.error(e, "Invalid Jenkins URL");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckWebSocket(@QueryParameter boolean webSocket, @QueryParameter boolean directConnection, @QueryParameter String jenkinsTunnel) {
            if (webSocket) {
                if (!WebSockets.isSupported()) {
                    return FormValidation.error("WebSocket support is not enabled in this Jenkins installation");
                }
                if (Util.fixEmpty(jenkinsTunnel) != null) {
                    return FormValidation.error("Tunneling is not currently supported in WebSocket mode");
                }
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // used by jelly
        public List<Descriptor<PodRetention>> getAllowedPodRetentions() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return new ArrayList<>(0);
            }
            return DescriptorVisibilityFilter.apply(this, jenkins.getDescriptorList(PodRetention.class));
        }

        @SuppressWarnings({"rawtypes", "unused"}) // used by jelly
        public Descriptor getDefaultPodRetention() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return null;
            }
            return jenkins.getDescriptor(PodRetention.getKubernetesCloudDefault().getClass());
        }

        @SuppressWarnings("unused") // used by jelly
        public int getDefaultReadTimeout() {
            return DEFAULT_READ_TIMEOUT_SECONDS;
        }

        @SuppressWarnings("unused") // used by jelly
        public int getDefaultConnectTimeout() {
            return DEFAULT_CONNECT_TIMEOUT_SECONDS;
        }

        @SuppressWarnings("unused") // used by jelly
        public int getDefaultRetentionTimeout() {
            return DEFAULT_RETENTION_TIMEOUT_MINUTES;
        }

        public int getDefaultWaitForPod() {
            return DEFAULT_WAIT_FOR_POD_SEC;
        }

    }

    @Override
    public String toString() {
        return "KubernetesCloud{" +
                "defaultsProviderTemplate='" + defaultsProviderTemplate + '\'' +
                ", templates=" + templates +
                ", serverUrl='" + serverUrl + '\'' +
                ", serverCertificate='" + serverCertificate + '\'' +
                ", skipTlsVerify=" + skipTlsVerify +
                ", addMasterProxyEnvVars=" + addMasterProxyEnvVars +
                ", capOnlyOnAlivePods=" + capOnlyOnAlivePods +
                ", namespace='" + namespace + '\'' +
                ", jenkinsUrl='" + jenkinsUrl + '\'' +
                ", jenkinsTunnel='" + jenkinsTunnel + '\'' +
                ", credentialsId='" + credentialsId + '\'' +
                ", containerCap=" + containerCap +
                ", retentionTimeout=" + retentionTimeout +
                ", connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", labels=" + labels +
                ", podLabels=" + podLabels +
                ", usageRestricted=" + usageRestricted +
                ", maxRequestsPerHost=" + maxRequestsPerHost +
                ", waitForPodSec=" + waitForPodSec +
                ", podRetention=" + podRetention +
                ", useJenkinsProxy=" + useJenkinsProxy +
                '}';
    }

    private Object readResolve() {
        if ((serverCertificate != null) && !serverCertificate.trim().startsWith("-----BEGIN CERTIFICATE-----")) {
            serverCertificate = new String(Base64.decodeBase64(serverCertificate.getBytes(UTF_8)), UTF_8);
            LOGGER.log(Level.INFO, "Upgraded Kubernetes server certificate key: {0}",
                    serverCertificate.substring(0, 80));
        }

        if (maxRequestsPerHost == 0) {
            maxRequestsPerHost = DEFAULT_MAX_REQUESTS_PER_HOST;
        }
        if (podRetention == null) {
            podRetention = PodRetention.getKubernetesCloudDefault();
        }
        setConnectTimeout(connectTimeout);
        setReadTimeout(readTimeout);
        setRetentionTimeout(retentionTimeout);
        if (waitForPodSec == null) {
            waitForPodSec = DEFAULT_WAIT_FOR_POD_SEC;
        }
        if (podLabels == null && labels != null) {
            setPodLabels(PodLabel.fromMap(labels));
        }

        return this;
    }

    @Extension
    public static class PodTemplateSourceImpl extends PodTemplateSource {
        @Nonnull
        @Override
        public List<PodTemplate> getList(@Nonnull KubernetesCloud cloud) {
            return cloud.getTemplates();
        }
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void hpiRunInit() {
        if (Main.isDevelopmentMode) {
            Jenkins jenkins = Jenkins.get();
            String hostAddress = System.getProperty("jenkins.host.address");
            if (hostAddress != null && jenkins.clouds.getAll(KubernetesCloud.class).isEmpty()) {
                KubernetesCloud cloud = new KubernetesCloud("kubernetes");
                cloud.setJenkinsUrl("http://" + hostAddress + ":8080/jenkins/");
                jenkins.clouds.add(cloud);
            }
        }
    }
}
