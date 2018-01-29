package org.csanchez.jenkins.plugins.kubernetes;

import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import static java.util.logging.Level.*;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.security.ACL;
import hudson.util.Secret;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubernetesFactoryAdapter {

    private static final Logger LOGGER = Logger.getLogger(KubernetesFactoryAdapter.class.getName());

    private static final int DEFAULT_CONNECT_TIMEOUT = 5;
    private static final int DEFAULT_READ_TIMEOUT = 15;

    private final String serviceAddress;
    private final String namespace;
    @CheckForNull
    private final String caCertData;
    @CheckForNull
    private final StandardCredentials credentials;

    private final boolean skipTlsVerify;
    private final int connectTimeout;
    private final int readTimeout;
    private final int maxRequestsPerHost;

    public KubernetesFactoryAdapter(String serviceAddress, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify) {
        this(serviceAddress, null, caCertData, credentials, skipTlsVerify);
    }

    public KubernetesFactoryAdapter(String serviceAddress, String namespace, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify) {
        this(serviceAddress, namespace, caCertData, credentials, skipTlsVerify, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public KubernetesFactoryAdapter(String serviceAddress, String namespace, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify, int connectTimeout, int readTimeout) {
        this(serviceAddress, namespace, caCertData, credentials, skipTlsVerify, connectTimeout, readTimeout, KubernetesCloud.DEFAULT_MAX_REQUESTS_PER_HOST);
    }

    public KubernetesFactoryAdapter(String serviceAddress, String namespace, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify, int connectTimeout, int readTimeout, int maxRequestsPerHost) {
        this.serviceAddress = serviceAddress;
        this.namespace = namespace;
        this.caCertData = caCertData;
        this.credentials = credentials != null ? getCredentials(credentials) : null;
        this.skipTlsVerify = skipTlsVerify;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.maxRequestsPerHost = maxRequestsPerHost;
    }

    private StandardCredentials getCredentials(String credentials) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentials)
        );
    }

    public KubernetesClient createClient() throws NoSuchAlgorithmException, UnrecoverableKeyException,
            KeyStoreException, IOException, CertificateEncodingException {

        ConfigBuilder builder;
        // autoconfigure if url is not set
        if (StringUtils.isBlank(serviceAddress)) {
            LOGGER.log(FINE, "Autoconfiguring Kubernetes client");
            builder = new ConfigBuilder(Config.autoConfigure());
        } else {
            // although this will still autoconfigure based on Config constructor notes
            // In future releases (2.4.x) the public constructor will be empty.
            // The current functionality will be provided by autoConfigure().
            // This is a necessary change to allow us distinguish between auto configured values and builder values.
            builder = new ConfigBuilder().withMasterUrl(serviceAddress);
        }

        builder = builder.withRequestTimeout(readTimeout * 1000).withConnectionTimeout(connectTimeout * 1000);

        if (!StringUtils.isBlank(namespace)) {
            builder.withNamespace(namespace);
        } else if (StringUtils.isBlank(builder.getNamespace())) {
            builder.withNamespace("default");
        }

        if (credentials instanceof StringCredentials) {
            final String token = ((StringCredentials) credentials).getSecret().getPlainText();
            builder.withOauthToken(token);
        } else if (credentials instanceof TokenProducer) {
            final String token = ((TokenProducer) credentials).getToken(serviceAddress, caCertData, skipTlsVerify);
            builder.withOauthToken(token);
        } else if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials usernamePassword = (UsernamePasswordCredentials) credentials;
            builder.withUsername(usernamePassword.getUsername())
                    .withPassword(Secret.toString(usernamePassword.getPassword()));
        } else if (credentials instanceof StandardCertificateCredentials) {
            StandardCertificateCredentials certificateCredentials = (StandardCertificateCredentials) credentials;
            KeyStore keyStore = certificateCredentials.getKeyStore();
            String alias = keyStore.aliases().nextElement();
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            Key key = keyStore.getKey(alias, Secret.toString(certificateCredentials.getPassword()).toCharArray());
            builder.withClientCertData(Base64.encodeBase64String(certificate.getEncoded()))
                    .withClientKeyData(pemEncodeKey(key))
                    .withClientKeyPassphrase(Secret.toString(certificateCredentials.getPassword()));
        }

        if (skipTlsVerify) {
            builder.withTrustCerts(true);
        }

        if (caCertData != null) {
            // JENKINS-38829 CaCertData expects a Base64 encoded certificate
            builder.withCaCertData(Base64.encodeBase64String(caCertData.getBytes(UTF_8)));
        }
        builder.withMaxConcurrentRequestsPerHost(maxRequestsPerHost);

        LOGGER.log(FINE, "Creating Kubernetes client: {0}", this.toString());
        return new DefaultKubernetesClient(builder.build());
    }

    private static String pemEncodeKey(Key key) {
        return Base64.encodeBase64String(new StringBuilder() //
                .append("-----BEGIN PRIVATE KEY-----\n") //
                .append(Base64.encodeBase64String(key.getEncoded())) //
                .append("\n-----END PRIVATE KEY-----\n") //
                .toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "KubernetesFactoryAdapter [serviceAddress=" + serviceAddress + ", namespace=" + namespace
                + ", caCertData=" + caCertData + ", credentials=" + credentials + ", skipTlsVerify=" + skipTlsVerify
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
