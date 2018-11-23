package org.csanchez.jenkins.plugins.kubernetes;

import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateMap;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Default;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.base.Strings;
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
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * Kubernetes cloud provider.
 *
 * Starts agents in a Kubernetes cluster using defined Docker templates for each label.
 *
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesCloud extends Cloud {
    public static final int DEFAULT_MAX_REQUESTS_PER_HOST = 32;

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());

    public static final String JNLP_NAME = "jnlp";
    /** label for all pods started by the plugin */
    @Deprecated
    public static final Map<String, String> DEFAULT_POD_LABELS = ImmutableMap.of("jenkins", "slave");

    /** Default timeout for idle workers that don't correctly indicate exit. */
    private static final int DEFAULT_RETENTION_TIMEOUT_MINUTES = 5;

    private String defaultsProviderTemplate;

    @Nonnull
    private List<PodTemplate> templates = new ArrayList<>();
    private String serverUrl;
    @CheckForNull
    private String serverCertificate;

    private boolean skipTlsVerify;
    private boolean addMasterProxyEnvVars;

    private boolean capOnlyOnAlivePods;

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
    private Map<String, String> labels;
    private boolean usageRestricted;

    private int maxRequestsPerHost;
    @CheckForNull
    private PodRetention podRetention = PodRetention.getKubernetesCloudDefault();

    @DataBoundConstructor
    public KubernetesCloud(String name) {
        super(name);
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
        this.defaultsProviderTemplate = source.defaultsProviderTemplate;
        this.templates.addAll(source.templates);
        this.serverUrl = source.serverUrl;
        this.skipTlsVerify = source.skipTlsVerify;
        this.addMasterProxyEnvVars = source.addMasterProxyEnvVars;
        this.namespace = source.namespace;
        this.jenkinsUrl = source.jenkinsUrl;
        this.jenkinsTunnel = source.jenkinsTunnel;
        this.credentialsId = source.credentialsId;
        this.containerCap = source.containerCap;
        this.retentionTimeout = source.retentionTimeout;
        this.connectTimeout = source.connectTimeout;
        this.usageRestricted = source.usageRestricted;
        this.podRetention = source.podRetention;
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
        this.retentionTimeout = retentionTimeout;
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
        JenkinsLocationConfiguration locationConfiguration = JenkinsLocationConfiguration.get();
        String url = StringUtils.defaultIfBlank(
                getJenkinsUrl(),
                StringUtils.defaultIfBlank(
                        System.getProperty("KUBERNETES_JENKINS_URL",System.getenv("KUBERNETES_JENKINS_URL")),
                        locationConfiguration.getUrl()
                )
        );
        if (url == null) {
            throw new IllegalStateException("Jenkins URL for Kubernetes is null");
        }
        url = url.endsWith("/") ? url : url + "/";
        return url;
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

    /**
     * Labels for all pods started by the plugin
     */
    public Map<String, String> getLabels() {
        return labels == null || labels.isEmpty() ? DEFAULT_POD_LABELS : labels;
    }

    /**
     * No UI yet, so this is never re-set
     * 
     * @param labels
     */
    // @DataBoundSetter
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    @DataBoundSetter
    public void setMaxRequestsPerHostStr(String maxRequestsPerHostStr) {
        try  {
            this.maxRequestsPerHost = Integer.parseInt(maxRequestsPerHostStr);
        } catch (NumberFormatException e) {
            maxRequestsPerHost = DEFAULT_MAX_REQUESTS_PER_HOST;
        }
    }

    public String getMaxRequestsPerHostStr() {
        return String.valueOf(maxRequestsPerHost);
    }

    public int getMaxRequestsPerHost() {
        return maxRequestsPerHost;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
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
    public KubernetesClient connect() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
            IOException, CertificateEncodingException {

        LOGGER.log(Level.FINE, "Building connection to Kubernetes {0} URL {1} namespace {2}",
                new String[] { getDisplayName(), serverUrl, namespace });
        KubernetesClient client = KubernetesClientProvider.createClient(this);

        LOGGER.log(Level.FINE, "Connected to Kubernetes {0} URL {1}", new String[] { getDisplayName(), serverUrl });
        return client;
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(@CheckForNull final Label label, final int excessWorkload) {
        try {
            Set<String> allInProvisioning = InProvisioning.getAllInProvisioning(label);
            LOGGER.log(Level.FINE, () -> "In provisioning : " + allInProvisioning);
            int toBeProvisioned = Math.max(0, excessWorkload - allInProvisioning.size());
            LOGGER.log(Level.INFO, "Excess workload after pending Kubernetes agents: {0}", toBeProvisioned);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

            for (PodTemplate t: getTemplatesFor(label)) {
                LOGGER.log(Level.INFO, "Template for label {0}: {1}", new Object[] { label, t.getDisplayName() });
                for (int i = 0; i < toBeProvisioned; i++) {
                    if (!addProvisionedSlave(t, label, i)) {
                        break;
                    }
                    r.add(PlannedNodeBuilderFactory.createInstance().cloud(this).template(t).label(label).build());
                }
                LOGGER.log(Level.FINEST, "Planned Kubernetes agents for template \"{0}\": {1}",
                        new Object[] { t.getDisplayName(), r.size() });
                if (r.size() > 0) {
                    // Already found a matching template
                    return r;
                }
            }
            return r;
        } catch (KubernetesClientException e) {
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

    /**
     * Check not too many already running.
     *
     */
    private boolean addProvisionedSlave(@Nonnull PodTemplate template, @CheckForNull Label label, int scheduledCount) throws Exception {
        if (containerCap == 0) {
            return true;
        }

        KubernetesClient client = connect();
        String templateNamespace = template.getNamespace();
        // If template's namespace is not defined, take the
        // Kubernetes Namespace.
        if (Strings.isNullOrEmpty(templateNamespace)) {
            templateNamespace = client.getNamespace();
        }

        PodList slaveList = client.pods().inNamespace(templateNamespace).withLabels(getLabels()).list();
        List<Pod> allActiveSlavePods = null;
        // JENKINS-53370 check for nulls
        if (slaveList != null && slaveList.getItems() != null) {
            allActiveSlavePods = slaveList.getItems().stream() //
                    .filter(x -> x.getStatus().getPhase().toLowerCase().matches("(running|pending)"))
                    .collect(Collectors.toList());
        }

        Map<String, String> labelsMap = new HashMap<>(this.getLabels());
        labelsMap.putAll(template.getLabelsMap());
        PodList templateSlaveList = client.pods().inNamespace(templateNamespace).withLabels(labelsMap).list();
        // JENKINS-53370 check for nulls
        List<Pod> activeTemplateSlavePods = null;
        if (templateSlaveList != null && templateSlaveList.getItems() != null) {
            activeTemplateSlavePods = templateSlaveList.getItems().stream()
                    .filter(x -> x.getStatus().getPhase().toLowerCase().matches("(running|pending)"))
                    .collect(Collectors.toList());
        }

        if (allActiveSlavePods != null && containerCap <= allActiveSlavePods.size() + scheduledCount) {
            LOGGER.log(Level.INFO,
                    "Total container cap of {0} reached, not provisioning: {1} running or pending in namespace {2} with Kubernetes labels {3}",
                    new Object[] { containerCap, allActiveSlavePods.size() + scheduledCount, templateNamespace, getLabels() });
            return false;
        }

        if (activeTemplateSlavePods != null && allActiveSlavePods != null && template.getInstanceCap() <= activeTemplateSlavePods.size() + scheduledCount) {
            LOGGER.log(Level.INFO,
                    "Template instance cap of {0} reached for template {1}, not provisioning: {2} running or pending in namespace {3} with label \"{4}\" and Kubernetes labels {5}",
                    new Object[] { template.getInstanceCap(), template.getName(), activeTemplateSlavePods.size() + scheduledCount,
                            templateNamespace, label == null ? "" : label.toString(), labelsMap });
            return false;
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
        return PodTemplateUtils.getTemplateByLabel(label, getAllTemplates());
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
        public FormValidation doTestConnection(@QueryParameter String name, @QueryParameter String serverUrl, @QueryParameter String credentialsId,
                                               @QueryParameter String serverCertificate,
                                               @QueryParameter boolean skipTlsVerify,
                                               @QueryParameter String namespace,
                                               @QueryParameter int connectionTimeout,
                                               @QueryParameter int readTimeout) throws Exception {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (StringUtils.isBlank(name))
                return FormValidation.error("name is required");

            try {
                try (KubernetesClient client = new KubernetesFactoryAdapter(serverUrl, namespace,
                        Util.fixEmpty(serverCertificate), Util.fixEmpty(credentialsId), skipTlsVerify,
                        connectionTimeout, readTimeout).createClient()) {
                    // test listing pods
                    client.pods().list();
                }
                return FormValidation.ok("Connection test successful");
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
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String serverUrl) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return new StandardListBoxModel().withEmptySelection() //
                    .withMatching( //
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(FileCredentials.class),
                                    CredentialsMatchers.instanceOf(TokenProducer.class),
                                    CredentialsMatchers.instanceOf(
                                            org.jenkinsci.plugins.kubernetes.credentials.TokenProducer.class),
                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                    CredentialsMatchers.instanceOf(StringCredentials.class)), //
                            CredentialsProvider.lookupCredentials(StandardCredentials.class, //
                                    Jenkins.getInstance(), //
                                    ACL.SYSTEM, //
                                    serverUrl != null ? URIRequirementBuilder.fromUri(serverUrl).build()
                                            : Collections.EMPTY_LIST //
                            ));

        }

        @RequirePOST
        public FormValidation doCheckMaxRequestsPerHostStr(@QueryParameter String value) throws IOException, ServletException {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Please supply an integer");
            }
        }

        public List<Descriptor<PodRetention>> getAllowedPodRetentions() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return new ArrayList<>(0);
            }
            return DescriptorVisibilityFilter.apply(this, jenkins.getDescriptorList(PodRetention.class));
        }

        @SuppressWarnings("rawtypes")
        public Descriptor getDefaultPodRetention() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return null;
            }
            return jenkins.getDescriptor(PodRetention.getKubernetesCloudDefault().getClass());
        }

    }

    @Override
    public String toString() {
        return String.format("KubernetesCloud name: %s serverUrl: %s", name, serverUrl);
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
}
