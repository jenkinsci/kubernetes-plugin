package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.concurrent.TimeUnit;

/**
 * Created by fbelzunc on 22/11/2018.
 */
public final class KubernetesClientHelper {

    private static final Cache<String, KubernetesClient> clients;

    private static final Integer CACHE_SIZE;
    private static final Integer CACHE_TTL;

    static {
        CACHE_SIZE = Integer.getInteger("org.csanchez.jenkins.plugins.kubernetes.clients.cacheSize", 10);
        CACHE_TTL = Integer.getInteger("org.csanchez.jenkins.plugins.kubernetes.clients.cacheTtl", 60);
        clients = CacheBuilder.newBuilder()
                .maximumSize(CACHE_SIZE)
                .expireAfterWrite(CACHE_TTL, TimeUnit.MINUTES)
                .build();
    }

    private KubernetesClientHelper() {
    }

    public static KubernetesClient createClient(String cloudName, String serviceAddress, String namespace, @CheckForNull String caCertData,
                                          @CheckForNull String credentials, boolean skipTlsVerify, int connectTimeout, int readTimeout, int maxRequestsPerHost) throws NoSuchAlgorithmException, UnrecoverableKeyException,
            KeyStoreException, IOException, CertificateEncodingException {

        KubernetesClient client = new KubernetesFactoryAdapter(serviceAddress, namespace, caCertData, credentials, skipTlsVerify,
                connectTimeout, readTimeout, maxRequestsPerHost).createClient();

        return client;
    }
}
