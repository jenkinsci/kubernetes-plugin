package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.model.PeriodicWork;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.SaveableListener;
import io.fabric8.kubernetes.client.HttpClientAware;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

/**
 * Manages the Kubernetes client creation per cloud
 */
@Restricted(NoExternalUse.class) // testing only
public class KubernetesClientProvider {

    private static final Logger LOGGER = Logger.getLogger(KubernetesClientProvider.class.getName());

    /**
     * How many clouds can we connect to, default to 10
     */
    private static final Integer CACHE_SIZE = Integer
            .getInteger(KubernetesClientProvider.class.getPackage().getName() + ".clients.cacheSize", 10);

    /**
     * Time in seconds after which we will close the unused clients, default to one hour
     */
    private static final Long EXPIRED_CLIENTS_PURGE_TIME = Long.getLong(
            KubernetesClientProvider.class.getPackage().getName() + ".clients.expiredClientsPurgeTime", 1 * 60 * 60);
    /**
     * How often to check if we need to close clients, default to {@link #EXPIRED_CLIENTS_PURGE_TIME}/2
     */
    private static final Long EXPIRED_CLIENTS_PURGE_PERIOD = Long.getLong(
            KubernetesClientProvider.class.getPackage().getName() + ".clients.expiredClientsPurgePeriod",
            EXPIRED_CLIENTS_PURGE_TIME / 2);

    /**
     * Client expiration in seconds, default to one day
     */
    private static final Integer CACHE_EXPIRATION = Integer.getInteger(
            KubernetesClientProvider.class.getPackage().getName() + ".clients.cacheExpiration", 24 * 60 * 60);

    private static final Queue<Client> expiredClients = new ConcurrentLinkedQueue<>();

    private static final Cache<String, Client> clients = CacheBuilder.newBuilder() //
            .maximumSize(CACHE_SIZE) //
            .expireAfterWrite(CACHE_EXPIRATION, TimeUnit.SECONDS) //
            .removalListener(rl -> {
                LOGGER.log(Level.FINE, "{0} cache : Removing entry for {1}",
                        new Object[] { KubernetesClient.class.getSimpleName(), rl.getKey() });
                Client client = (Client) rl.getValue();
                if (client != null) {
                    client.expired = Instant.now();
                    expiredClients.add(client);
                }

            }) //
            .build();

    private KubernetesClientProvider() {
    }

    static KubernetesClient createClient(KubernetesCloud cloud) throws NoSuchAlgorithmException,
            UnrecoverableKeyException, KeyStoreException, IOException, CertificateEncodingException {
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
        private Instant expired;

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

        public Instant getExpired() {
            return expired;
        }
    }

    @Extension
    public static class PurgeExpiredKubernetesClients extends AsyncPeriodicWork {

        public PurgeExpiredKubernetesClients() {
            super("Purge expired KubernetesClients");
        }

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(EXPIRED_CLIENTS_PURGE_PERIOD);
        }

        @Override
        protected Level getNormalLoggingLevel() {
            return Level.FINEST;
        }

        @Override
        protected void execute(TaskListener listener) {
            closeExpiredClients();
        }
    }

    /**
     * Gracefully close expired clients
     * 
     * @return whether some clients have been closed or not
     */
    @Restricted(NoExternalUse.class) // testing only
    public static boolean closeExpiredClients() {
        boolean b = false;
        if (expiredClients.isEmpty()) {
            return b;
        }
        LOGGER.log(Level.FINE, "Closing expired clients: ({0}) {1}",
                new Object[] { expiredClients.size(), expiredClients });
        if (expiredClients.size() > 10) {
            LOGGER.log(Level.WARNING, "High number of expired clients, may cause memory leaks: ({0}) {1}",
                    new Object[] { expiredClients.size(), expiredClients });
        }
        for (Iterator<Client> it = expiredClients.iterator(); it.hasNext();) {
            Client expiredClient = it.next();
            // only purge it if the EXPIRED_CLIENTS_PURGE time has elapsed
            if (Instant.now().minus(EXPIRED_CLIENTS_PURGE_TIME, ChronoUnit.SECONDS)
                    .isBefore(expiredClient.getExpired())) {
                break;
            }
            KubernetesClient client = expiredClient.client;
            if (client instanceof HttpClientAware) {
                if (gracefulClose(client, ((HttpClientAware) client).getHttpClient())) {
                    it.remove();
                    b = true;
                }
            } else {
                LOGGER.log(Level.WARNING, "{0} is not {1}, forcing close",
                        new Object[] { client.toString(), HttpClientAware.class.getSimpleName() });
                client.close();
                it.remove();
                b = true;
            }
        }
        return b;
    }

    @Restricted(NoExternalUse.class) // testing only
    public static boolean gracefulClose(KubernetesClient client, OkHttpClient httpClient) {
        Dispatcher dispatcher = httpClient.dispatcher();
        // Remove the client if there are no more users
        int runningCallsCount = dispatcher.runningCallsCount();
        int queuedCallsCount = dispatcher.queuedCallsCount();
        if (runningCallsCount == 0 && queuedCallsCount == 0) {
            LOGGER.log(Level.INFO, "Closing {0}", client.toString());
            client.close();
            return true;
        } else {
            LOGGER.log(Level.INFO, "Not closing {0}: there are still running ({1}) or queued ({2}) calls",
                    new Object[] { client.toString(), runningCallsCount, queuedCallsCount });
            return false;
        }
    }

    private static volatile int runningCallsCount;
    private static volatile int queuedCallsCount;

    public static int getRunningCallsCount() {
        return runningCallsCount;
    }

    public static int getQueuedCallsCount() {
        return queuedCallsCount;
    }

    @Restricted(NoExternalUse.class) // testing only
    public static void invalidate(String displayName) {
        clients.invalidate(displayName);
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
                    LOGGER.log(Level.INFO, "Invalidating Kubernetes client: {0}", displayName);
                    invalidate(displayName);
                }
            }
            super.onChange(o, file);
        }
    }

    @Extension
    public static class UpdateConnectionCount extends PeriodicWork {

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(5);
        }

        @Override
        protected void doRun() {
            int runningCallsCount = 0;
            int queuedCallsCount = 0;
            for (Client client : KubernetesClientProvider.clients.asMap().values()) {
                KubernetesClient kClient = client.getClient();
                if (kClient instanceof HttpClientAware) {
                    OkHttpClient httpClient = ((HttpClientAware) kClient).getHttpClient();
                    Dispatcher dispatcher = httpClient.dispatcher();
                    runningCallsCount += dispatcher.runningCallsCount();
                    queuedCallsCount += dispatcher.queuedCallsCount();
                }
            }
            KubernetesClientProvider.runningCallsCount = runningCallsCount;
            KubernetesClientProvider.queuedCallsCount = queuedCallsCount;
        }
    }
}
