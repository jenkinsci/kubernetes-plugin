package org.csanchez.jenkins.plugins.kubernetes;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.sanitizeLabel;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.Main;
import hudson.TcpSlaveAgentListener;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Label;
import hudson.model.Saveable;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.DescribableList;
import hudson.util.FormApply;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.XStream2;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.bouncycastle.api.PEMEncodable;
import jenkins.metrics.api.Metrics;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import jenkins.security.FIPS140;
import jenkins.util.SystemProperties;
import jenkins.websocket.WebSockets;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateMap;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Default;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.watch.PodStatusEventHandler;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuth;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

/**
 * Kubernetes cloud provider.
 *
 * Starts agents in a Kubernetes cluster using defined Docker templates for each label.
 *
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesCloud extends Cloud implements PodTemplateGroup {
    public static final int DEFAULT_MAX_REQUESTS_PER_HOST = 32;
    public static final Integer DEFAULT_WAIT_FOR_POD_SEC = 600;

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());

    public static final String JNLP_NAME = "jnlp";
    /** label for all pods started by the plugin */
    @Deprecated
    public static final Map<String, String> DEFAULT_POD_LABELS = Collections.singletonMap("jenkins", "slave");

    /** Default timeout for idle workers that don't correctly indicate exit. */
    public static final int DEFAULT_RETENTION_TIMEOUT_MINUTES = 5;

    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 15;

    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;

    private String defaultsProviderTemplate;

    @NonNull
    private List<PodTemplate> templates = new ArrayList<>();

    private String serverUrl;
    private boolean useJenkinsProxy;

    @CheckForNull
    private String serverCertificate;

    private boolean skipTlsVerify;
    private boolean addMasterProxyEnvVars;

    private boolean capOnlyOnAlivePods;

    private String namespace;
    private String jnlpregistry;
    private boolean restrictedPssSecurityContext = false;
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

    @CheckForNull
    private GarbageCollection garbageCollection;

    @NonNull
    private DescribableList<KubernetesCloudTrait, KubernetesCloudTraitDescriptor> traits =
            new DescribableList<>(Saveable.NOOP);

    /**
     * namespace -> informer
     * Use to watch pod events per namespace.
     */
    private transient volatile Map<String, SharedIndexInformer<Pod>> informers = new ConcurrentHashMap<>();

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
    public KubernetesCloud(
            String name,
            List<? extends PodTemplate> templates,
            String serverUrl,
            String namespace,
            String jenkinsUrl,
            String containerCapStr,
            int connectTimeout,
            int readTimeout,
            int retentionTimeout) {
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

    public boolean isUseJenkinsProxy() {
        return useJenkinsProxy;
    }

    @DataBoundSetter
    public void setUseJenkinsProxy(boolean useJenkinsProxy) {
        this.useJenkinsProxy = useJenkinsProxy;
    }

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
        this.defaultsProviderTemplate = Util.fixEmpty(defaultsProviderTemplate);
    }

    @NonNull
    public List<PodTemplate> getTemplates() {
        return templates;
    }

    /**
     * Returns all pod templates for this cloud including the dynamic ones.
     * @return all pod templates for this cloud including the dynamic ones.
     */
    @NonNull
    public List<PodTemplate> getAllTemplates() {
        return PodTemplateSource.getAll(this);
    }

    @DataBoundSetter
    public void setTemplates(@NonNull List<PodTemplate> templates) {
        this.templates = new ArrayList<>(templates);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(@NonNull String serverUrl) {
        ensureKubernetesUrlInFipsMode(serverUrl);
        this.serverUrl = Util.fixEmpty(serverUrl);
    }

    public String getServerCertificate() {
        return serverCertificate;
    }

    @DataBoundSetter
    public void setServerCertificate(String serverCertificate) {
        ensureServerCertificateInFipsMode(serverCertificate);
        this.serverCertificate = Util.fixEmpty(serverCertificate);
    }

    public boolean isSkipTlsVerify() {
        return skipTlsVerify;
    }

    @DataBoundSetter
    public void setSkipTlsVerify(boolean skipTlsVerify) {
        ensureSkipTlsVerifyInFipsMode(skipTlsVerify);
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

    public String getJnlpregistry() {
        return jnlpregistry;
    }

    @DataBoundSetter
    public void setJnlpregistry(String jnlpregistry) {
        this.jnlpregistry = Util.fixEmpty(jnlpregistry);
    }

    public boolean isRestrictedPssSecurityContext() {
        return restrictedPssSecurityContext;
    }

    @DataBoundSetter
    public void setRestrictedPssSecurityContext(boolean restrictedPssSecurityContext) {
        this.restrictedPssSecurityContext = restrictedPssSecurityContext;
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

    public GarbageCollection getGarbageCollection() {
        return garbageCollection;
    }

    @DataBoundSetter
    public void setGarbageCollection(GarbageCollection garbageCollection) {
        this.garbageCollection = garbageCollection;
    }

    /**
     * Get list of traits enabled for this cloud.
     * @return configured traits, never null
     */
    @NonNull
    public List<KubernetesCloudTrait> getTraits() {
        return traits;
    }

    /**
     * Replace the traits enabled for this cloud.
     * @param traits configured traits, if {@code null} traits will be cleared.
     */
    @DataBoundSetter
    public void setTraits(List<KubernetesCloudTrait> traits) {
        this.traits = new DescribableList<>(Saveable.NOOP, Util.fixNull(traits));
    }

    /**
     * Find configuration trait by type.
     * @param traitType trait type class
     * @return configuration trait or empty if not configured
     * @param <T> trait type
     */
    @NonNull
    public <T extends KubernetesCloudTrait> Optional<T> getTrait(Class<T> traitType) {
        return Optional.ofNullable(this.traits.get(traitType));
    }

    /**
     * @return same as {@link #getJenkinsUrlOrNull}, if set
     * @throws IllegalStateException if no Jenkins URL could be computed.
     */
    @NonNull
    public String getJenkinsUrlOrDie() {
        String url = getJenkinsUrlOrNull();
        if (url == null) {
            throw new IllegalStateException("Jenkins URL for Kubernetes is null");
        }
        return url;
    }

    /**
     * Jenkins URL to be used by agents launched by this cloud.
     *
     * <p>Tries in order:<ol>
     * <li>an explicitly configured URL ({@link #getJenkinsUrl})
     * <li>the system property or environment variable {@code KUBERNETES_JENKINS_URL}, unless {@link #isWebSocket} mode and {@link #getCredentialsId} is defined
     * <li>{@link JenkinsLocationConfiguration#getUrl}
     * </ol>
     *
     * @return Jenkins URL to be used by agents launched by this cloud. Always ends with a trailing slash.
     *         Null if no Jenkins URL could be computed.
     */
    @CheckForNull
    public String getJenkinsUrlOrNull() {
        String url = getJenkinsUrl();
        if (url == null && (!isWebSocket() || getCredentialsId() == null)) {
            url = Util.fixEmpty(System.getProperty("KUBERNETES_JENKINS_URL", System.getenv("KUBERNETES_JENKINS_URL")));
        }
        if (url == null) {
            url = JenkinsLocationConfiguration.get().getUrl();
        }
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
        return (containerCap == null || containerCap == Integer.MAX_VALUE || containerCap == 0)
                ? ""
                : String.valueOf(containerCap);
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
        try {
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
    public KubernetesClient connect() throws KubernetesAuthException, IOException {

        LOGGER.log(Level.FINEST, "Building connection to Kubernetes {0} URL {1} namespace {2}", new String[] {
            getDisplayName(), serverUrl, namespace
        });
        KubernetesClient client = KubernetesClientProvider.createClient(this);

        LOGGER.log(Level.FINE, "Connected to Kubernetes {0} URL {1} namespace {2}", new String[] {
            getDisplayName(), client.getMasterUrl().toString(), namespace
        });
        return client;
    }

    /**
     * Get {@link PodResource} from {@link KubernetesClient}.
     * @param namespace namespace pod is located in, possibly null
     * @param name pod name, not null
     * @return pod resource, never null
     * @throws KubernetesAuthException if cluster authentication failed
     * @throws IOException if connection failed
     * @see #connect()
     * @see #getPodResource(KubernetesClient, String, String)
     */
    @NonNull
    public PodResource getPodResource(@Nullable String namespace, @NonNull String name)
            throws KubernetesAuthException, IOException {
        return getPodResource(connect(), namespace, name);
    }

    /**
     * Get {@link PodResource} from {@link KubernetesClient}.
     * @param client kubernetes client, not null
     * @param namespace namespace pod is located in, possibly null
     * @param name pod name, not null
     * @return pod resource, never null
     * @throws KubernetesAuthException if cluster authentication failed
     * @throws IOException if connection failed
     * @see #connect()
     */
    @NonNull
    static PodResource getPodResource(
            @NonNull KubernetesClient client, @Nullable String namespace, @NonNull String name) {
        if (namespace == null) {
            return client.pods().withName(name);
        } else {
            return client.pods().inNamespace(namespace).withName(name);
        }
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(
            @NonNull final Cloud.CloudState state, final int excessWorkload) {
        var limitRegistrationResults = new LimitRegistrationResults(this);
        try {
            Label label = state.getLabel();
            // Planned nodes, will be launched on the next round of NodeProvisioner
            int plannedCapacity = state.getAdditionalPlannedCapacity();
            Set<String> allInProvisioning = InProvisioning.getAllInProvisioning(label); // Nodes being launched
            LOGGER.log(Level.FINE, () -> "In provisioning : " + allInProvisioning);
            int toBeProvisioned = Math.max(0, excessWorkload - allInProvisioning.size());
            List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<>();
            LOGGER.log(Level.FINE, "Label \"{0}\" excess workload: {1}, executors: {2}", new Object[] {
                label, toBeProvisioned, plannedCapacity
            });

            for (PodTemplate podTemplate : getTemplatesFor(label)) {
                LOGGER.log(Level.FINE, "Template for label \"{0}\": {1}", new Object[] {label, podTemplate.getName()});
                // check overall concurrency limit using the default label(s) on all templates
                int numExecutors = 1;
                PodTemplate unwrappedTemplate = getUnwrappedTemplate(podTemplate);
                while (toBeProvisioned > 0 && limitRegistrationResults.register(podTemplate, numExecutors)) {
                    plannedNodes.add(PlannedNodeBuilderFactory.createInstance()
                            .cloud(this)
                            .template(unwrappedTemplate)
                            .label(label)
                            .numExecutors(1)
                            .build());
                    toBeProvisioned--;
                }
                if (!plannedNodes.isEmpty()) {
                    // Return early when a matching template was found and nodes were planned
                    LOGGER.log(Level.FINEST, "Planned {0} Kubernetes agents with template \"{1}\"", new Object[] {
                        plannedNodes.size(), podTemplate.getName()
                    });
                    Metrics.metricRegistry()
                            .counter(MetricNames.PROVISION_NODES)
                            .inc(plannedNodes.size());
                    return plannedNodes;
                }
            }
            Metrics.metricRegistry().counter(MetricNames.PROVISION_NODES).inc(plannedNodes.size());
            return plannedNodes;
        } catch (KubernetesClientException e) {
            Metrics.metricRegistry().counter(MetricNames.PROVISION_FAILED).inc();
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException) {
                LOGGER.log(Level.WARNING, "Failed to connect to Kubernetes at {0}: {1}", new String[] {
                    serverUrl, cause.getMessage()
                });
            } else {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to count the # of live instances on Kubernetes",
                        cause != null ? cause : e);
            }
            limitRegistrationResults.unregister();
        } catch (Exception e) {
            Metrics.metricRegistry().counter(MetricNames.PROVISION_FAILED).inc();
            LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Kubernetes", e);
            limitRegistrationResults.unregister();
        }
        return Collections.emptyList();
    }

    /**
     * Checks if URL is using HTTPS, required in FIPS mode
     * Continues if URL is secure or not in FIPS mode, throws an {@link IllegalArgumentException} if not.
     * @param url Kubernetes server URL
     */
    private static void ensureKubernetesUrlInFipsMode(String url) {
        if (!FIPS140.useCompliantAlgorithms() || StringUtils.isBlank(url)) {
            return;
        }
        if (!url.startsWith("https:")) {
            throw new IllegalArgumentException(Messages.KubernetesCloud_kubernetesServerUrlIsNotSecure());
        }
    }

    /**
     * Checks if TLS verification is being skipped, which is not allowed in FIPS mode
     * Continues if not being skipped or not in FIPS mode, throws an {@link IllegalArgumentException} if not.
     * @param skipTlsVerify value to check
     */
    private static void ensureSkipTlsVerifyInFipsMode(boolean skipTlsVerify) {
        if (FIPS140.useCompliantAlgorithms() && skipTlsVerify) {
            throw new IllegalArgumentException(Messages.KubernetesCloud_skipTlsVerifyNotAllowedInFIPSMode());
        }
    }

    /**
     * Checks if server certificate is allowed if FIPS mode.
     * Allowed certificates use a public key with the following algorithms and sizes:
     * <ul>
     *     <li>DSA with key size >= 2048</li>
     *     <li>RSA with key size >= 2048</li>
     *     <li>Elliptic curve (ED25519) with field size >= 224</li>
     * </ul>
     * If certificate is valid and allowed or not in FIPS mode method will just exit.
     * If not it will throw an {@link IllegalArgumentException}.
     * @param serverCertificate String containing the certificate PEM.
     */
    private static void ensureServerCertificateInFipsMode(String serverCertificate) {
        if (!FIPS140.useCompliantAlgorithms()) {
            return;
        }
        if (StringUtils.isBlank(serverCertificate)) {
            return; // JENKINS-73789, no certificate is accepted
        }
        try {
            PEMEncodable pem = PEMEncodable.decode(serverCertificate);
            Certificate cert = pem.toCertificate();
            if (cert == null) {
                throw new IllegalArgumentException(Messages.KubernetesCloud_serverCertificateNotACertificate());
            }
            PublicKey publicKey = cert.getPublicKey();
            if (publicKey instanceof RSAPublicKey) {
                if (((RSAPublicKey) publicKey).getModulus().bitLength() < 2048) {
                    throw new IllegalArgumentException(Messages.KubernetesCloud_serverCertificateKeySize());
                }
            } else if (publicKey instanceof DSAPublicKey) {
                if (((DSAPublicKey) publicKey).getParams().getP().bitLength() < 2048) {
                    throw new IllegalArgumentException(Messages.KubernetesCloud_serverCertificateKeySize());
                }
            } else if (publicKey instanceof ECPublicKey) {
                if (((ECPublicKey) publicKey).getParams().getCurve().getField().getFieldSize() < 224) {
                    throw new IllegalArgumentException(Messages.KubernetesCloud_serverCertificateKeySizeEC());
                }
            }
        } catch (RuntimeException | UnrecoverableKeyException | IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public void replaceTemplate(PodTemplate oldTemplate, PodTemplate newTemplate) {
        this.checkManagePermission();
        this.removeTemplate(oldTemplate);
        this.addTemplate(newTemplate);
    }

    @Override
    public Permission getManagePermission() {
        return Jenkins.MANAGE;
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
    @CheckForNull
    public PodTemplate getTemplate(@CheckForNull Label label) {
        return PodTemplateUtils.getTemplateByLabel(label, getAllTemplates());
    }

    @SuppressWarnings("unused ") // stapler
    @CheckForNull
    public PodTemplate getTemplate(@NonNull String id) {
        return getTemplateById(id);
    }

    @CheckForNull
    public PodTemplate getTemplateById(@NonNull String id) {
        return getAllTemplates().stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .orElse(null);
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
    @Override
    public void addTemplate(PodTemplate t) {
        this.checkManagePermission();
        this.templates.add(t);
        // t.parent = this;
    }

    /**
     * Remove a
     *
     * @param t docker template
     */
    @Override
    public void removeTemplate(PodTemplate t) {
        this.checkManagePermission();
        this.templates.remove(t);
    }

    @Override
    public String getPodTemplateGroupUrl() {
        return "../../templates";
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
        return Objects.equals(name, that.name)
                && skipTlsVerify == that.skipTlsVerify
                && addMasterProxyEnvVars == that.addMasterProxyEnvVars
                && capOnlyOnAlivePods == that.capOnlyOnAlivePods
                && Objects.equals(containerCap, that.containerCap)
                && retentionTimeout == that.retentionTimeout
                && connectTimeout == that.connectTimeout
                && readTimeout == that.readTimeout
                && usageRestricted == that.usageRestricted
                && maxRequestsPerHost == that.maxRequestsPerHost
                && Objects.equals(defaultsProviderTemplate, that.defaultsProviderTemplate)
                && templates.equals(that.templates)
                && Objects.equals(serverUrl, that.serverUrl)
                && Objects.equals(serverCertificate, that.serverCertificate)
                && Objects.equals(namespace, that.namespace)
                && Objects.equals(jnlpregistry, that.jnlpregistry)
                && Objects.equals(jenkinsUrl, that.jenkinsUrl)
                && Objects.equals(jenkinsTunnel, that.jenkinsTunnel)
                && Objects.equals(credentialsId, that.credentialsId)
                && Objects.equals(getPodLabels(), that.getPodLabels())
                && Objects.equals(podRetention, that.podRetention)
                && Objects.equals(waitForPodSec, that.waitForPodSec)
                && Objects.equals(garbageCollection, that.garbageCollection)
                && useJenkinsProxy == that.useJenkinsProxy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                defaultsProviderTemplate,
                templates,
                serverUrl,
                serverCertificate,
                skipTlsVerify,
                addMasterProxyEnvVars,
                capOnlyOnAlivePods,
                namespace,
                jnlpregistry,
                jenkinsUrl,
                jenkinsTunnel,
                credentialsId,
                containerCap,
                retentionTimeout,
                connectTimeout,
                readTimeout,
                podLabels,
                usageRestricted,
                maxRequestsPerHost,
                podRetention,
                useJenkinsProxy,
                garbageCollection);
    }

    public Integer getWaitForPodSec() {
        return waitForPodSec;
    }

    @DataBoundSetter
    public void setWaitForPodSec(Integer waitForPodSec) {
        this.waitForPodSec = waitForPodSec;
    }

    @Restricted(NoExternalUse.class) // jelly
    public PodTemplate.DescriptorImpl getTemplateDescriptor() {
        return (PodTemplate.DescriptorImpl) Jenkins.get().getDescriptorOrDie(PodTemplate.class);
    }

    /**
     * Creating a new template.
     */
    @POST
    public HttpResponse doCreate(StaplerRequest2 req, StaplerResponse2 rsp)
            throws IOException, ServletException, Descriptor.FormException {
        Jenkins j = Jenkins.get();
        this.checkManagePermission();
        PodTemplate newTemplate = getTemplateDescriptor().newInstance(req, req.getSubmittedForm());
        addTemplate(newTemplate);
        j.save();
        // take the user back.
        return FormApply.success("templates");
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
            Jenkins.XSTREAM2.addCompatibilityAlias(
                    "org.csanchez.jenkins.plugins.kubernetes.ServiceAccountCredential",
                    org.jenkinsci.plugins.kubernetes.credentials.FileSystemServiceAccountCredential.class);
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doTestConnection(
                @AncestorInPath ItemGroup owner,
                @QueryParameter String name,
                @QueryParameter String serverUrl,
                @QueryParameter String credentialsId,
                @QueryParameter String serverCertificate,
                @QueryParameter boolean skipTlsVerify,
                @QueryParameter String namespace,
                @QueryParameter int connectionTimeout,
                @QueryParameter int readTimeout,
                @QueryParameter boolean useJenkinsProxy) {

            AccessControlled _context = (owner instanceof AccessControlled ? (AccessControlled) owner : Jenkins.get());

            checkPermission(_context);

            if (StringUtils.isBlank(name)) return FormValidation.error("name is required");

            try (KubernetesClient client = new KubernetesFactoryAdapter(
                            serverUrl,
                            namespace,
                            Util.fixEmpty(serverCertificate),
                            Util.fixEmpty(credentialsId),
                            owner,
                            skipTlsVerify,
                            connectionTimeout,
                            readTimeout,
                            DEFAULT_MAX_REQUESTS_PER_HOST,
                            useJenkinsProxy)
                    .createClient()) {
                // test listing pods
                client.pods().list();
                VersionInfo version = client.getVersion();
                return FormValidation.ok("Connected to Kubernetes " + version.getGitVersion());
            } catch (KubernetesClientException e) {
                LOGGER.log(Level.FINE, String.format("Error testing connection %s", serverUrl), e);
                return FormValidation.error(
                        "Error testing connection %s: %s",
                        serverUrl,
                        e.getCause() == null
                                ? e.getMessage()
                                : String.format(
                                        "%s: %s",
                                        e.getCause().getClass().getName(),
                                        e.getCause().getMessage()));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, String.format("Error testing connection %s", serverUrl), e);
                return FormValidation.error("Error testing connection %s: %s", serverUrl, e.getMessage());
            }
        }

        @RequirePOST
        @SuppressWarnings({"unused", "lgtm[jenkins/csrf]"
        }) // used by jelly and already fixed jenkins security scan warning
        public FormValidation doCheckSkipTlsVerify(
                @AncestorInPath AccessControlled owner, @QueryParameter boolean skipTlsVerify) {
            if (!hasPermission(owner)) {
                return FormValidation.ok();
            }
            try {
                ensureSkipTlsVerifyInFipsMode(skipTlsVerify);
            } catch (IllegalArgumentException ex) {
                return FormValidation.error(ex, ex.getLocalizedMessage());
            }
            return FormValidation.ok();
        }

        @RequirePOST
        @SuppressWarnings({"unused", "lgtm[jenkins/csrf]"
        }) // used by jelly and already fixed jenkins security scan warning
        public FormValidation doCheckServerCertificate(
                @AncestorInPath AccessControlled owner, @QueryParameter String serverCertificate) {
            if (!hasPermission(owner)) {
                return FormValidation.ok();
            }
            try {
                ensureServerCertificateInFipsMode(serverCertificate);
            } catch (IllegalArgumentException ex) {
                return FormValidation.error(ex, ex.getLocalizedMessage());
            }
            return FormValidation.ok();
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckServerUrl(
                @AncestorInPath AccessControlled owner, @QueryParameter String serverUrl) {
            if (!hasPermission(owner)) {
                return FormValidation.ok();
            }
            try {
                ensureKubernetesUrlInFipsMode(serverUrl);
            } catch (IllegalArgumentException ex) {
                return FormValidation.error(ex.getLocalizedMessage());
            }
            return FormValidation.ok();
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath ItemGroup owner, @QueryParameter String serverUrl) {
            checkPermission((owner instanceof AccessControlled ? (AccessControlled) owner : Jenkins.get()));
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            result.includeMatchingAs(
                    ACL.SYSTEM,
                    owner,
                    StandardCredentials.class,
                    serverUrl != null ? URIRequirementBuilder.fromUri(serverUrl).build() : Collections.EMPTY_LIST,
                    CredentialsMatchers.anyOf(AuthenticationTokens.matcher(KubernetesAuth.class)));
            return result;
        }

        @RequirePOST
        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckMaxRequestsPerHostStr(@QueryParameter String value)
                throws IOException, ServletException {
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
        @RequirePOST
        public FormValidation doCheckDirectConnection(
                @AncestorInPath AccessControlled owner,
                @QueryParameter boolean value,
                @QueryParameter String jenkinsUrl,
                @QueryParameter boolean webSocket) {
            if (!hasPermission(owner)) {
                return FormValidation.ok();
            }
            if (!webSocket) {
                TcpSlaveAgentListener tcpSlaveAgentListener = Jenkins.get().getTcpSlaveAgentListener();
                if (tcpSlaveAgentListener == null) {
                    return FormValidation.warning(
                            "'TCP port for inbound agents' is disabled in Global Security settings. Connecting Kubernetes agents will not work without this or WebSocket mode!");
                }
                if (tcpSlaveAgentListener.getIdentityPublicKey() == null) {
                    return FormValidation.error(
                            "You must install the instance-identity plugin to use inbound agents in TCP mode");
                }
            }

            if (value) {
                if (webSocket) {
                    return FormValidation.error("Direct connection and WebSocket mode are mutually exclusive");
                }
                if (!isEmpty(jenkinsUrl))
                    return FormValidation.warning("No need to configure Jenkins URL when direct connection is enabled");

                if (Jenkins.get().getSlaveAgentPort() == 0)
                    return FormValidation.warning(
                            "A random 'TCP port for inbound agents' is configured in Global Security settings. In 'direct connection' mode agents will not be able to reconnect to a restarted controller with random port!");
            } else {
                if (isEmpty(jenkinsUrl)) {
                    String url = StringUtils.defaultIfBlank(
                            System.getProperty("KUBERNETES_JENKINS_URL", System.getenv("KUBERNETES_JENKINS_URL")),
                            JenkinsLocationConfiguration.get().getUrl());
                    if (url != null) {
                        return FormValidation.ok("Will connect using " + url);
                    } else {
                        return FormValidation.warning("Configure either Direct Connection or Jenkins URL");
                    }
                }
            }
            return FormValidation.ok();
        }

        private static boolean hasPermission(AccessControlled owner) {
            if (owner instanceof Jenkins) {
                // Regular cloud
                return owner.hasPermission(Jenkins.ADMINISTER);
            } else if (owner instanceof Item) {
                // Shared cloud (CloudBees CI)
                return owner.hasPermission(Item.CONFIGURE);
            } else {
                LOGGER.log(
                        Level.WARNING,
                        () -> "Unsupported owner type " + (owner == null ? "null" : owner.getClass()) + " (url: "
                                + Stapler.getCurrentRequest2().getOriginalRequestURI()
                                + "). Please report this issue to the plugin maintainers.");
                return false;
            }
        }

        private static void checkPermission(AccessControlled owner) {
            if (owner instanceof Jenkins) {
                // Regular cloud
                owner.checkPermission(Jenkins.ADMINISTER);
            } else if (owner instanceof Item) {
                // Shared cloud (CloudBees CI)
                owner.checkPermission(Item.CONFIGURE);
            } else {
                throw new IllegalArgumentException(
                        "Unsupported owner type " + (owner == null ? "null" : owner.getClass()) + " (url: "
                                + Stapler.getCurrentRequest2().getOriginalRequestURI()
                                + "). Please report this issue to the plugin maintainers.");
            }
        }

        @SuppressWarnings("unused") // used by jelly
        public FormValidation doCheckJenkinsUrl(@QueryParameter String value, @QueryParameter boolean directConnection)
                throws IOException, ServletException {
            try {
                if (!isEmpty(value)) new URL(value);
            } catch (MalformedURLException e) {
                return FormValidation.error(e, "Invalid Jenkins URL");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // used by jelly
        @RequirePOST
        public FormValidation doCheckWebSocket(
                @AncestorInPath AccessControlled owner,
                @QueryParameter boolean webSocket,
                @QueryParameter boolean directConnection,
                @QueryParameter String jenkinsTunnel) {
            if (!hasPermission(owner)) {
                return FormValidation.ok();
            }
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
            return jenkins.getDescriptor(
                    PodRetention.getKubernetesCloudDefault().getClass());
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

        @SuppressWarnings("unused") // used by jelly
        public List<? extends KubernetesCloudTraitDescriptor> getAllTraits() {
            return KubernetesCloudTrait.all();
        }

        @SuppressWarnings("unused") // used by jelly
        public DescribableList<KubernetesCloudTrait, KubernetesCloudTraitDescriptor> getDefaultTraits() {
            return new DescribableList<>(Saveable.NOOP, KubernetesCloudTrait.getDefaultTraits());
        }
    }

    @Override
    public String toString() {
        return "KubernetesCloud{name=" + name + ", defaultsProviderTemplate='"
                + defaultsProviderTemplate + '\'' + ", serverUrl='"
                + serverUrl + '\'' + ", serverCertificate='"
                + serverCertificate + '\'' + ", skipTlsVerify="
                + skipTlsVerify + ", addMasterProxyEnvVars="
                + addMasterProxyEnvVars + ", capOnlyOnAlivePods="
                + capOnlyOnAlivePods + ", namespace='"
                + namespace + '\'' + ", jnlpregistry='"
                + jnlpregistry + '\'' + ", jenkinsUrl='"
                + jenkinsUrl + '\'' + ", jenkinsTunnel='"
                + jenkinsTunnel + '\'' + ", credentialsId='"
                + credentialsId + '\'' + ", webSocket="
                + webSocket + ", containerCap="
                + containerCap + ", retentionTimeout="
                + retentionTimeout + ", connectTimeout="
                + connectTimeout + ", readTimeout="
                + readTimeout + ", labels="
                + labels + ", podLabels="
                + podLabels + ", usageRestricted="
                + usageRestricted + ", maxRequestsPerHost="
                + maxRequestsPerHost + ", waitForPodSec="
                + waitForPodSec + ", podRetention="
                + podRetention + ", useJenkinsProxy="
                + useJenkinsProxy + ", templates="
                + templates + ", garbageCollection="
                + garbageCollection + '}';
    }

    private Object readResolve() {
        if ((serverCertificate != null) && !serverCertificate.trim().startsWith("-----BEGIN CERTIFICATE-----")) {
            serverCertificate = new String(Base64.getDecoder().decode(serverCertificate.getBytes(UTF_8)), UTF_8);
            LOGGER.log(
                    Level.INFO, "Upgraded Kubernetes server certificate key: {0}", serverCertificate.substring(0, 80));
        }

        // FIPS checks if in FIPS mode
        ensureServerCertificateInFipsMode(serverCertificate);
        ensureKubernetesUrlInFipsMode(serverUrl);
        ensureSkipTlsVerifyInFipsMode(skipTlsVerify);

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
        if (containerCap != null && containerCap == 0) {
            containerCap = null;
        }
        return this;
    }

    @Override
    public Cloud reconfigure(@NonNull StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
        // cloud configuration doesn't contain templates anymore, so just keep existing ones.
        var newInstance = (KubernetesCloud) super.reconfigure(req, form);
        newInstance.setTemplates(this.templates);
        return newInstance;
    }

    public void registerPodInformer(KubernetesSlave node) {
        // even having readResolve initializing informers is not enough, there are some special cases where XStream will
        // not call it, so let us make sure it is initialized before using
        if (informers == null) {
            synchronized (this) {
                if (informers == null) {
                    informers = new ConcurrentHashMap<>();
                }
            }
        }
        informers.computeIfAbsent(node.getNamespace(), (n) -> {
            KubernetesClient client;
            try {
                client = connect();
            } catch (KubernetesAuthException | IOException e) {
                LOGGER.log(
                        Level.WARNING,
                        "Cannot connect to K8s cloud. Pod events will not be available in build logs.",
                        e);
                return null;
            }
            Map<String, String> labelsFilter = new HashMap<>(getPodLabelsMap());
            String jenkinsUrlLabel = sanitizeLabel(getJenkinsUrlOrNull());
            if (jenkinsUrlLabel != null) {
                labelsFilter.put(PodTemplateBuilder.LABEL_KUBERNETES_CONTROLLER, jenkinsUrlLabel);
            }
            SharedIndexInformer<Pod> inform = client.pods()
                    .inNamespace(node.getNamespace())
                    .withLabels(labelsFilter)
                    .inform(new PodStatusEventHandler(), TimeUnit.SECONDS.toMillis(30));
            LOGGER.info(String.format(
                    "Registered informer to watch pod events on namespace [%s], with labels [%s] on cloud [%s]",
                    namespace, labelsFilter, name));
            return inform;
        });
    }

    @Extension
    public static class PodTemplateSourceImpl extends PodTemplateSource {
        @NonNull
        @Override
        public List<PodTemplate> getList(@NonNull KubernetesCloud cloud) {
            return cloud.getTemplates();
        }
    }

    @Initializer(after = InitMilestone.SYSTEM_CONFIG_LOADED)
    public static void hpiRunInit() {
        if (Main.isDevelopmentMode) {
            Jenkins jenkins = Jenkins.get();
            String hostAddress = System.getProperty("jenkins.host.address");
            if (hostAddress != null
                    && jenkins.clouds.getAll(KubernetesCloud.class).isEmpty()) {
                KubernetesCloud cloud = new KubernetesCloud("kubernetes");
                cloud.setJenkinsUrl(
                        "http://" + hostAddress + ":" + SystemProperties.getInteger("port", 8080) + "/jenkins/");
                jenkins.clouds.add(cloud);
            }
        }
    }
}
