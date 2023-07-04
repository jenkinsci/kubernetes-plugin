package org.csanchez.jenkins.plugins.kubernetes;


import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ProxyConfiguration;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuth;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthConfig;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;


import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINE;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 *
 * This class has been deprecated. You should use KubernetesClientProvider
 */
@Restricted(NoExternalUse.class)
@Deprecated
public class KubernetesFactoryAdapter {

    private static final Logger LOGGER = Logger.getLogger(KubernetesFactoryAdapter.class.getName());

    private static final int DEFAULT_CONNECT_TIMEOUT = 5;
    private static final int DEFAULT_READ_TIMEOUT = 15;

    private final String serviceAddress;
    private final String namespace;
    @CheckForNull
    private final String caCertData;
    @CheckForNull
    private final KubernetesAuth auth;

    private final boolean skipTlsVerify;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxRequestsPerHost;
    private final boolean useJenkinsProxy;

    public KubernetesFactoryAdapter(String serviceAddress, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify) throws KubernetesAuthException {
        this(serviceAddress, null, caCertData, credentials, skipTlsVerify);
    }

    public KubernetesFactoryAdapter(String serviceAddress, String namespace, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify) throws KubernetesAuthException {
        this(serviceAddress, namespace, caCertData, credentials, skipTlsVerify, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public KubernetesFactoryAdapter(String serviceAddress, String namespace, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify, int connectTimeout, int readTimeout) throws KubernetesAuthException {
        this(serviceAddress, namespace, caCertData, credentials, skipTlsVerify, connectTimeout, readTimeout, KubernetesCloud.DEFAULT_MAX_REQUESTS_PER_HOST,false);
    }

    public KubernetesFactoryAdapter(String serviceAddress, String namespace, @CheckForNull String caCertData,
                                    @CheckForNull String credentialsId, boolean skipTlsVerify, int connectTimeout, int readTimeout, int maxRequestsPerHost, boolean useJenkinsProxy) throws KubernetesAuthException {
        this.serviceAddress = serviceAddress;
        this.namespace = namespace;
        this.caCertData = decodeBase64IfNeeded(caCertData);
        this.auth = AuthenticationTokens.convert(KubernetesAuth.class, resolveCredentials(credentialsId));
        this.skipTlsVerify = skipTlsVerify;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxRequestsPerHost = maxRequestsPerHost;
        this.useJenkinsProxy = useJenkinsProxy;
    }

    private static String decodeBase64IfNeeded(String caCertData) {
        if (Util.fixEmpty(caCertData) != null) {
            try {
                // Decode Base64 if needed
                byte[] decode = Base64.getDecoder().decode(caCertData.getBytes(UTF_8));
                return new String(decode, UTF_8);
            } catch (IllegalArgumentException e) {
                return caCertData;
            }
        }
        return caCertData;
    }

    public KubernetesClient createClient() throws KubernetesAuthException {

        ConfigBuilder builder;

        if (StringUtils.isBlank(serviceAddress)) {
            LOGGER.log(FINE, "Autoconfiguring Kubernetes client");
            builder = new ConfigBuilder(Config.autoConfigure(null));
        } else {
            // Using Config.empty() disables autoconfiguration when both serviceAddress and auth are set
            builder = auth == null ? new ConfigBuilder() : new ConfigBuilder(Config.empty());
            builder = builder.withMasterUrl(serviceAddress);
        }

        if (auth != null) {
            builder = auth.decorate(builder, new KubernetesAuthConfig(builder.getMasterUrl(), caCertData, skipTlsVerify));
            // If authentication is provided, disable autoconfigure flag to deactivate auto refresh
            builder = builder.withAutoConfigure(false);
        }

        if (skipTlsVerify) {
            builder.withTrustCerts(true);
        }

        if (caCertData != null) {
            // JENKINS-38829 CaCertData expects a Base64 encoded certificate
            builder.withCaCertData(Base64.getEncoder().encodeToString(caCertData.getBytes(UTF_8)));
        }

        builder = builder.withRequestTimeout(readTimeout * 1000).withConnectionTimeout(connectTimeout * 1000);
        builder.withMaxConcurrentRequestsPerHost(maxRequestsPerHost);
        builder.withMaxConcurrentRequests(maxRequestsPerHost);

        if (!StringUtils.isBlank(namespace)) {
            builder.withNamespace(namespace);
        } else if (StringUtils.isBlank(builder.getNamespace())) {
            builder.withNamespace("default");
        }

        LOGGER.log(FINE, "Creating Kubernetes client: {0}", this.toString());
        // JENKINS-63584 If Jenkins has a configured Proxy and cloud has enabled proxy usage pass the arguments to K8S
        LOGGER.log(FINE, "Proxy Settings for Cloud: " + useJenkinsProxy);
        if(useJenkinsProxy) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            LOGGER.log(FINE, "Jenkins Instance: " + jenkins);
            if (jenkins != null) {
                ProxyConfiguration p = jenkins.proxy;
                LOGGER.log(FINE,"Proxy Instance: " + p);
                if (p != null) {
                    builder.withHttpsProxy("http://" + p.name + ":" + p.port);
                    builder.withHttpProxy("http://" + p.name + ":" + p.port);
                    String proxyUserName = p.getUserName();
                    if (proxyUserName != null) {
                        String password = getProxyPasswordDecrypted(p);
                        builder.withProxyUsername(proxyUserName);
                        builder.withProxyPassword(password);
                    }
                    builder.withNoProxy(getNoProxyHosts(p));
                }
            }
        }
        return new KubernetesClientBuilder().withConfig(builder.build()).build();
    }

    /**
     * Get the no proxy hosts in the format supported by the Kubernetes Client implementation. In particular
     * <code>*</code> are not supported.
     *
     * For Example:
     * * <code>example.com</code> to not use the proxy for <code>example.com</code> and its subdomain.
     * * <code>.example.com</code> to not use the proxy for subdomains of <code>example.com</code>. But use it
     * for <code>.example.com</code>.
     *
     * Note: Jenkins Proxy supports wildcard such as <code>192.168.*</code> or <code>*my*.example.com</code> that cannot
     * be converted to the current kubernetes client implementation.
     *
     * @see https://github.com/fabric8io/kubernetes-client/blob/master/CHANGELOG.md#610-2022-08-31
     * @see https://www.gnu.org/software/wget/manual/html_node/Proxies.html.
     * @param proxy a {@link ProxyConfiguration}
     * @return the array of no proxy hosts
     */
    private String[] getNoProxyHosts(@NonNull ProxyConfiguration proxy) {
        Set<String> noProxyHosts = new HashSet<>();
        for (String noProxyHost : proxy.getNoProxyHost().split("\n")) {
            noProxyHosts.add(noProxyHost.replace("*", ""));
        }
        return noProxyHosts.toArray(new String[0]);
    }

    private String getProxyPasswordDecrypted(ProxyConfiguration p) {
        String passwordEncrypted = p.getPassword();
        String password = null;
        if (passwordEncrypted != null) {
            Secret secret = Secret.fromString(passwordEncrypted);
            password = Secret.toString(secret);
        }
        return password;
    }
    @Override
    public String toString() {
        return "KubernetesFactoryAdapter [serviceAddress=" + serviceAddress + ", namespace=" + namespace
                + ", caCertData=" + caCertData + ", credentials=" + auth + ", skipTlsVerify=" + skipTlsVerify
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }

    @CheckForNull
    private static StandardCredentials resolveCredentials(@CheckForNull String credentialsId) throws KubernetesAuthException {
        if (credentialsId == null) {
            return null;
        }
        StandardCredentials c = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()
                ),
                CredentialsMatchers.allOf(
                        AuthenticationTokens.matcher(KubernetesAuth.class),
                        CredentialsMatchers.withId(credentialsId)
                )
        );
        if (c == null) {
            throw new KubernetesAuthException("No credentials found with id " + credentialsId);
        }
        return c;
    }

}
