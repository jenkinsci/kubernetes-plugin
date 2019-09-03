package org.csanchez.jenkins.plugins.kubernetes;

import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import static java.util.logging.Level.*;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jenkinsci.plugins.kubernetes.auth.*;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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
        this(serviceAddress, namespace, caCertData, credentials, skipTlsVerify, connectTimeout, readTimeout, KubernetesCloud.DEFAULT_MAX_REQUESTS_PER_HOST);
    }

    public KubernetesFactoryAdapter(String serviceAddress, String namespace, @CheckForNull String caCertData,
                                    @CheckForNull String credentialsId, boolean skipTlsVerify, int connectTimeout, int readTimeout, int maxRequestsPerHost) throws KubernetesAuthException {
        this.serviceAddress = serviceAddress;
        this.namespace = namespace;
        this.caCertData = caCertData;
        this.auth = credentialsId != null ? KubernetesAuthFactory.fromCredentialsId(credentialsId, serviceAddress, caCertData, skipTlsVerify) : null;
        this.skipTlsVerify = skipTlsVerify;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxRequestsPerHost = maxRequestsPerHost;
    }

    public KubernetesClient createClient() throws IOException, KubernetesAuthException {

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
            // Using assignment here due to KubernetesAuthKubeconfig - it returns new ConfigBuilder
            builder = auth.decorate(builder);
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
        return new DefaultKubernetesClient(builder.build());
    }

    @Override
    public String toString() {
        return "KubernetesFactoryAdapter [serviceAddress=" + serviceAddress + ", namespace=" + namespace
                + ", caCertData=" + caCertData + ", credentials=" + auth + ", skipTlsVerify=" + skipTlsVerify
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
