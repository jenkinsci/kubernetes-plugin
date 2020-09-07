package org.csanchez.jenkins.plugins.kubernetes;


import java.util.Collections;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.ProxyConfiguration;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
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
        this.caCertData = caCertData;
        this.auth = AuthenticationTokens.convert(KubernetesAuth.class, resolveCredentials(credentialsId));
        this.skipTlsVerify = skipTlsVerify;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxRequestsPerHost = maxRequestsPerHost;
        this.useJenkinsProxy = useJenkinsProxy;
    }

    public KubernetesClient createClient() throws KubernetesAuthException {

        ConfigBuilder builder;

        if (StringUtils.isBlank(serviceAddress)) {
            LOGGER.log(FINE, "Autoconfiguring Kubernetes client");
            builder = new ConfigBuilder(Config.autoConfigure(null));
        } else {
            // although this will still autoconfigure based on Config constructor notes
            // In future releases (2.4.x) the public constructor will be empty.
            // The current functionality will be provided by autoConfigure().
            // This is a necessary change to allow us distinguish between auto configured values and builder values.
            builder = new ConfigBuilder().withMasterUrl(serviceAddress);
        }

        if (auth != null) {
            builder = auth.decorate(builder, new KubernetesAuthConfig(builder.getMasterUrl(), caCertData, skipTlsVerify));
        }

        if (skipTlsVerify) {
            builder.withTrustCerts(true);
        }

        if (caCertData != null) {
            // JENKINS-38829 CaCertData expects a Base64 encoded certificate
            builder.withCaCertData(Base64.encodeBase64String(caCertData.getBytes(UTF_8)));
        }

        builder = builder.withRequestTimeout(readTimeout * 1000).withConnectionTimeout(connectTimeout * 1000);
        builder.withMaxConcurrentRequestsPerHost(maxRequestsPerHost);

        if (!StringUtils.isBlank(namespace)) {
            builder.withNamespace(namespace);
        } else if (StringUtils.isBlank(builder.getNamespace())) {
            builder.withNamespace("default");
        }

        LOGGER.log(FINE, "Creating Kubernetes client: {0}", this.toString());
        // JENKINS-63584 If Jenkins has an configured Proxy and cloud has enabled proxy usage pass the arguments to K8S
        LOGGER.log(FINE, "Proxy Settings for Cloud: " + useJenkinsProxy);
        if(useJenkinsProxy) {
            Jenkins jenkins = Jenkins.getInstanceOrNull(); // this code might run on slaves
            LOGGER.log(FINE, "Jenkins Instance: " + jenkins);
            if (jenkins != null) {
                ProxyConfiguration p = jenkins.proxy;
                LOGGER.log(FINE,"Proxy Instance: " + p);
                if (p != null) {
                    builder.withWebsocketTimeout(10000);
                    builder.withHttpsProxy("http://" + p.name + ":" + p.port);
                    builder.withHttpProxy("http://" + p.name + ":" + p.port);
                    if (p.name != null) {
                        String password = getProxyPasswordDecrypted(p);
                        builder.withProxyUsername(p.name);
                        builder.withProxyPassword(password);
                    }
                    builder.withNoProxy(p.getNoProxyHost().split("\n"));
                }
            }
        }
        return new DefaultKubernetesClient(builder.build());
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
    private static StandardCredentials resolveCredentials(@CheckForNull String credentialsId) {
        if (credentialsId == null) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
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
    }
}
