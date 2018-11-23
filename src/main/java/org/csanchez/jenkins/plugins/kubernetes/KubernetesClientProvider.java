package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;

import io.fabric8.kubernetes.client.KubernetesClient;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Kubernetes client creation per cloud
 */
final class KubernetesClientProvider {

    private static final Cache<String, Client> clients;

    private static final Integer CACHE_SIZE;
    private static final Integer CACHE_TTL;

    static {
        CACHE_SIZE = Integer.getInteger("org.csanchez.jenkins.plugins.kubernetes.clients.cacheSize", 10);
        CACHE_TTL = Integer.getInteger("org.csanchez.jenkins.plugins.kubernetes.clients.cacheTtl", 60);
        clients = CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .expireAfterWrite(CACHE_TTL, TimeUnit.MINUTES)
                .removalListener((RemovalListener<String, Client>) removalNotification -> {
                    // https://google.github.io/guava/releases/23.0/api/docs/com/google/common/cache/RemovalNotification.html
                    // A notification of the removal of a single entry. The key and/or value may be null if they were already garbage collected.
                    if (removalNotification.getValue() != null) {
                        removalNotification.getValue().getClient().close();
                    }
                })
                .build();
    }

    private KubernetesClientProvider() {
    }

    static KubernetesClient createClient(KubernetesCloud cloud) throws NoSuchAlgorithmException, UnrecoverableKeyException,
            KeyStoreException, IOException, CertificateEncodingException {

        final int validity = Objects.hashCode(cloud.getServerUrl(), cloud.getNamespace(), cloud.getServerCertificate(),
                cloud.getCredentialsId(), cloud.isSkipTlsVerify(), cloud.getConnectTimeout(), cloud.getReadTimeout(),
                cloud.getMaxRequestsPerHostStr());
        final Client c = clients.getIfPresent(cloud.getDisplayName());

        if (c != null && validity == c.getValidity()) {
            return c.getClient();
        } else {
            // expire tha cache if any of these config options have changed
            c.client.close();
            KubernetesClient client = new KubernetesFactoryAdapter(cloud.getServerUrl(), cloud.getNamespace(),
                    cloud.getServerCertificate(), cloud.getCredentialsId(), cloud.isSkipTlsVerify(),
                    cloud.getConnectTimeout(), cloud.getReadTimeout(), cloud.getMaxRequestsPerHost()).createClient();
            clients.put(cloud.getDisplayName(), new Client(validity, client));

            return client;
        }
    }

    private static class Client {
        private final KubernetesClient client;
        private final int validity;

        public Client(int validity, KubernetesClient client) {
            this.client = client;
            this.validity = validity;
        }

        public KubernetesClient getClient() {
            return client;
        }

        public int getValidity() {
            return validity;
        }
    }

}
