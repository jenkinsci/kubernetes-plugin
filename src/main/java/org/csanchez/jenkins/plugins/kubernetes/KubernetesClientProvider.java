package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.SaveableListener;
import jenkins.model.Jenkins;

/**
 * Manages the Kubernetes client creation per cloud
 */
final class KubernetesClientProvider {

    private static final Logger LOGGER = Logger.getLogger(KubernetesClientProvider.class.getName());

    /**
     * How many clouds can we connect to, default to 10
     */
    private static final Integer CACHE_SIZE = Integer
            .getInteger(KubernetesClientProvider.class.getPackage().getName() + ".clients.cacheSize", 10);

    /**
     * Client expiration in seconds, default to one day
     */
    private static final Integer CACHE_EXPIRATION = Integer.getInteger(
            KubernetesClientProvider.class.getPackage().getName() + ".clients.cacheExpiration", 24 * 60 * 60);

    private static final List<KubernetesClient> expiredClients = Collections.synchronizedList(new ArrayList());

    private static final Cache<String, Client> clients = CacheBuilder
            .newBuilder() //
            .maximumSize(CACHE_SIZE) //
            .expireAfterWrite(CACHE_EXPIRATION, TimeUnit.SECONDS) //
            .removalListener(rl -> {
                LOGGER.log(Level.FINE, "{0} cache : Removing entry for {1}", new Object[] {KubernetesClient.class.getSimpleName(), rl.getKey()});
                KubernetesClient client = ((Client) rl.getValue()).getClient();
                if (client != null) {
                    if (client instanceof HttpClientAware) {
                        if (!gracefulClose(client, ((HttpClientAware) client).getHttpClient())) {
                            expiredClients.add(client);
                        }
                    } else {
                        LOGGER.log(Level.WARNING, "{0} is not {1}, forcing close", new Object[] {client.toString(), HttpClientAware.class.getSimpleName()});
                        client.close();
                    }
                }

            }) //
            .build();

    private KubernetesClientProvider() {
    }

    static KubernetesClient createClient(KubernetesCloud cloud) throws NoSuchAlgorithmException, UnrecoverableKeyException,
            KeyStoreException, IOException, CertificateEncodingException {
        String displayName = cloud.getDisplayName();
        final Client c = clients.getIfPresent(displayName);
        if (c == null) {
            KubernetesClient client = new KubernetesFactoryAdapter(cloud.getServerUrl(), cloud.getNamespace(),
                    cloud.getServerCertificate(), cloud.getCredentialsId(), cloud.isSkipTlsVerify(),
                    cloud.getConnectTimeout(), cloud.getReadTimeout(), cloud.getMaxRequestsPerHost()).createClient();
            clients.put(displayName, new Client(getValidity(cloud), client));
            LOGGER.log(Level.INFO, "Created new Kubernetes client: {0} {1}", new Object[] { displayName, client });
            return client;
        }
        return c.getClient();
    }

    private static int getValidity(KubernetesCloud cloud) {
        return Objects.hashCode(cloud.getServerUrl(), cloud.getNamespace(), cloud.getServerCertificate(),
                cloud.getCredentialsId(), cloud.isSkipTlsVerify(), cloud.getConnectTimeout(), cloud.getReadTimeout(),
                cloud.getMaxRequestsPerHostStr());
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

    @Extension
    public static class PurgeExpiredKubernetesClients extends AsyncPeriodicWork {

        public PurgeExpiredKubernetesClients() {
            super("Purge expired KubernetesClients");
        }

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.MINUTES.toMillis(1);
        }

        @Override
        protected Level getNormalLoggingLevel() {
            return Level.FINEST;
        }

        @Override
        protected void execute(TaskListener listener) {
            for (Iterator<KubernetesClient> it = expiredClients.iterator(); it.hasNext();) {
                KubernetesClient client = it.next();
                if (client instanceof HttpClientAware) {
                    if (gracefulClose(client, ((HttpClientAware) client).getHttpClient())) {
                        it.remove();
                    }
                } else {
                    LOGGER.log(Level.WARNING, "{0} is not {1}, forcing close", new Object[] {client.toString(), HttpClientAware.class.getSimpleName()});
                    client.close();
                    it.remove();
                }
            }
        }
    }

    private static boolean gracefulClose(KubernetesClient client, OkHttpClient httpClient) {
        Dispatcher dispatcher = httpClient.dispatcher();
        // Remove the client if there are no more users
        int runningCallsCount = dispatcher.runningCallsCount();
        int queuedCallsCount = dispatcher.queuedCallsCount();
        if (runningCallsCount == 0 && queuedCallsCount == 0) {
            LOGGER.log(Level.INFO, "Closing {0}", client.toString());
            client.close();
            return true;
        } else {
            LOGGER.log(Level.INFO, "Not closing {0}: there are still running ({1}) or queued ({2}) calls", new Object[] {client.toString(), runningCallsCount, queuedCallsCount});
            return false;
        }
    }

    @Extension
    public static class SaveableListenerImpl extends SaveableListener {
        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Jenkins) {
                Jenkins jenkins = (Jenkins) o;
                Set<String> cloudDisplayNames = new HashSet<>(clients.asMap().keySet());
                for (KubernetesCloud cloud : jenkins.clouds.getAll(KubernetesCloud.class)) {
                    String displayName = cloud.getDisplayName();
                    Client client = clients.getIfPresent(displayName);
                    if (client != null && client.getValidity() == getValidity(cloud)) {
                        cloudDisplayNames.remove(displayName);
                    } else {
                        LOGGER.log(Level.INFO, "Invalidating Kubernetes client: {0} {1}",
                                new Object[] { displayName, client });
                    }
                }
                // Remove missing / invalid clients
                for (String displayName : cloudDisplayNames) {
                    clients.invalidate(displayName);
                }
            }
            super.onChange(o, file);
        }
    }

}
