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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import hudson.model.Environment;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.codec.binary.Base64;
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
import com.google.common.collect.ImmutableMap;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
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

/**
 * Kubernetes cloud provider.
 *
 * Starts slaves in a Kubernetes cluster using defined Docker templates for each label.
 *
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesCloud extends Cloud {
    public static final int DEFAULT_MAX_REQUESTS_PER_HOST = 32;

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());

    private static final String DEFAULT_ID = "jenkins/slave-default";

    public static final String JNLP_NAME = "jnlp";
    /** label for all pods started by the plugin */
    public static final Map<String, String> DEFAULT_POD_LABELS = ImmutableMap.of("jenkins", "slave");

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
    private int maxRequestsPerHost;

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
        this.namespace = source.namespace;
        this.jenkinsUrl = source.jenkinsUrl;
        this.credentialsId = source.credentialsId;
        this.containerCap = source.containerCap;
        this.retentionTimeout = source.retentionTimeout;
        this.connectTimeout = source.connectTimeout;
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

    @Nonnull
    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(@Nonnull String namespace) {
        Preconditions.checkArgument(!StringUtils.isBlank(namespace));
        this.namespace = namespace;
    }

    @CheckForNull
    public String getJenkinsUrl() {
        return jenkinsUrl;
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
        String locationConfigurationUrl = locationConfiguration != null ? locationConfiguration.getUrl() : null;
        String url = StringUtils.defaultIfBlank(
                getJenkinsUrl(),
                StringUtils.defaultIfBlank(
                        System.getProperty("KUBERNETES_JENKINS_URL",System.getenv("KUBERNETES_JENKINS_URL")),
                        locationConfigurationUrl
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

        LOGGER.log(Level.FINE, "Building connection to Kubernetes {0} URL {1}",
                new String[] { getDisplayName(), serverUrl });
        client = new KubernetesFactoryAdapter(serverUrl, namespace, serverCertificate, credentialsId, skipTlsVerify,
                connectTimeout, readTimeout, maxRequestsPerHost).createClient();
        LOGGER.log(Level.FINE, "Connected to Kubernetes {0} URL {1}", new String[] { getDisplayName(), serverUrl });
        return client;
    }

    private String getIdForLabel(Label label) {
        if (label == null) {
            return DEFAULT_ID;
        }
        return "jenkins/" + label.getName();
    }

    Map<String, String> getLabelsMap(Set<LabelAtom> labelSet) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
        builder.putAll(DEFAULT_POD_LABELS);
        if (!labelSet.isEmpty()) {
            for (LabelAtom label: labelSet) {
                builder.put(getIdForLabel(label), "true");
            }
        }
        return builder.build();
    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(@CheckForNull final Label label, final int excessWorkload) {
        try {

            LOGGER.log(Level.INFO, "Excess workload after pending Spot instances: " + excessWorkload);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<NodeProvisioner.PlannedNode>();

            ArrayList<PodTemplate> templates = getMatchingTemplates(label);

            for (PodTemplate t: templates) {
                LOGGER.log(Level.INFO, "Template: " + t.getDisplayName());
                for (int i = 1; i <= excessWorkload; i++) {
                    if (!addProvisionedSlave(t, label)) {
                        break;
                    }

                    r.add(new NodeProvisioner.PlannedNode(t.getDisplayName(), Computer.threadPoolForRemoting
                                .submit(new ProvisioningCallback(this, t, label)), 1));
                }
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
    private boolean addProvisionedSlave(@Nonnull PodTemplate template, @CheckForNull Label label) throws Exception {
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

        PodList slaveList = client.pods().inNamespace(templateNamespace).withLabels(DEFAULT_POD_LABELS).list();
        List<Pod> slaveListItems = slaveList.getItems();

        Map<String, String> labelsMap = getLabelsMap(template.getLabelSet());
        PodList namedList = client.pods().inNamespace(templateNamespace).withLabels(labelsMap).list();
        List<Pod> namedListItems = namedList.getItems();

        if (slaveListItems != null && containerCap <= slaveListItems.size()) {
            LOGGER.log(Level.INFO,
                    "Total container cap of {0} reached, not provisioning: {1} running or errored in namespace {2}",
                    new Object[] { containerCap, slaveListItems.size(), client.getNamespace() });
            return false;
        }

        if (namedListItems != null && slaveListItems != null && template.getInstanceCap() <= namedListItems.size()) {
            LOGGER.log(Level.INFO,
                    "Template instance cap of {0} reached for template {1}, not provisioning: {2} running or errored in namespace {3} with label {4}",
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
            if ((label == null && t.getNodeUsageMode() == Node.Mode.NORMAL) || (label != null && label.matches(t.getLabelSet()))) {
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

        public FormValidation doTestConnection(@QueryParameter String name, @QueryParameter String serverUrl, @QueryParameter String credentialsId,
                                               @QueryParameter String serverCertificate,
                                               @QueryParameter boolean skipTlsVerify,
                                               @QueryParameter String namespace,
                                               @QueryParameter int connectionTimeout,
                                               @QueryParameter int readTimeout) throws Exception {

            if (StringUtils.isBlank(name))
                return FormValidation.error("name is required");

            try {
                KubernetesClient client = new KubernetesFactoryAdapter(serverUrl, namespace,
                        Util.fixEmpty(serverCertificate), Util.fixEmpty(credentialsId), skipTlsVerify,
                        connectionTimeout, readTimeout).createClient();

                client.pods().list();
                return FormValidation.ok("Connection successful");
            } catch (KubernetesClientException e) {
                LOGGER.log(Level.FINE, String.format("Error connecting to %s", serverUrl), e);
                return FormValidation.error("Error connecting to %s: %s", serverUrl, e.getCause() == null
                        ? e.getMessage()
                        : String.format("%s: %s", e.getCause().getClass().getName(), e.getCause().getMessage()));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, String.format("Error connecting to %s", serverUrl), e);
                return FormValidation.error("Error connecting to %s: %s", serverUrl, e.getMessage());
            }
        }

        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String serverUrl) {
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
                                    serverUrl != null ? URIRequirementBuilder.fromUri(serverUrl).build()
                                                      : Collections.EMPTY_LIST
                            ));

        }

        public FormValidation doCheckMaxRequestsPerHostStr(@QueryParameter String value) throws IOException, ServletException {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Please supply an integer");
            }
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
        return this;
    }

}
