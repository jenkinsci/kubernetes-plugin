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
import java.util.concurrent.ExecutionException;
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
                    removalNotification.getValue().getClient().close();
                })
                .build();
    }

    private KubernetesClientProvider() {
    }

    static KubernetesClient createClient(final String cloudName, final String serviceAddress, final String namespace, @CheckForNull final String caCertData,
                                                @CheckForNull final String credentials, final boolean skipTlsVerify, final int connectTimeout, final int readTimeout, final int maxRequestsPerHost) throws NoSuchAlgorithmException, UnrecoverableKeyException,
            KeyStoreException, IOException, CertificateEncodingException, ExecutionException {

        // expire tha cache if any of these config options have changed
        final int validity = Objects.hashCode(serviceAddress, namespace, caCertData, credentials, skipTlsVerify, connectTimeout, readTimeout, maxRequestsPerHost);
        final Client c = clients.getIfPresent(cloudName);
        if (c != null && validity == c.getValidity()) {
            return c.getClient();
        } else {
            KubernetesClient client = new KubernetesFactoryAdapter(serviceAddress, namespace, caCertData, credentials, skipTlsVerify,
                    connectTimeout, readTimeout, maxRequestsPerHost).createClient();
            clients.put(cloudName, new Client(validity, client));

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
