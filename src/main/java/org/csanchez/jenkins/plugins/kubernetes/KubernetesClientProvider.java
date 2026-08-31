package org.csanchez.jenkins.plugins.kubernetes;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Reaper;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Manages the Kubernetes client creation per cloud
 */
public class KubernetesClientProvider {

    private static final Logger LOGGER = Logger.getLogger(KubernetesClientProvider.class.getName());

    /**
     * Client expiration in seconds.
     *
     * Some providers such as Amazon EKS use a token with 15 minutes expiration, so expire clients after 10 minutes.
     */
    private static final long CACHE_EXPIRATION = Long.getLong(
            KubernetesClientProvider.class.getPackage().getName() + ".clients.cacheExpiration",
            TimeUnit.MINUTES.toSeconds(10));

    /**
     * Extra safety margin added on top of a cloud's own connect+read timeout before a retired client is
     * actually closed, protecting any in-flight call that grabbed a reference just before eviction.
     * See JENKINS-76095.
     */
    private static final long CLOSE_GRACE_MARGIN_SECONDS =
            Long.getLong(KubernetesClientProvider.class.getPackage().getName() + ".clients.closeGraceMargin", 30);

    /**
     * Fallback grace period used only if the {@link KubernetesCloud} config can no longer be resolved at
     * eviction time (e.g. the cloud itself was deleted, not just updated).
     */
    private static final long DEFAULT_CLOSE_GRACE_PERIOD_SECONDS =
            Long.getLong(KubernetesClientProvider.class.getPackage().getName() + ".clients.closeGracePeriod", 60);

    /**
     * A single shared, bounded daemon-thread scheduler for deferred client closes. Deliberately NOT one
     * thread per client - that would just reintroduce a leak of a different shape. See JENKINS-76095.
     */
    private static final ScheduledExecutorService CLOSE_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory(
                    new DaemonThreadFactory(), KubernetesClientProvider.class.getName() + ".deferredClose"));

    private static final Cache<String, Client> clients = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_EXPIRATION, TimeUnit.SECONDS)
            .removalListener((key, value, cause) -> {
                Client client = (Client) value;
                if (client == null) {
                    return;
                }
                LOGGER.log(Level.FINE, () -> "Retiring Kubernetes client " + key + " " + client.client + ": " + cause);

                Jenkins jenkins = Jenkins.getInstanceOrNull();
                KubernetesCloud cloud = jenkins == null
                        ? null
                        : jenkins.clouds.getAll(KubernetesCloud.class).stream()
                                .filter(c -> c.getDisplayName().equals(key))
                                .findFirst()
                                .orElse(null);

                // (1) Proactively re-establish the Reaper's long-lived watch on a FRESH client for this
                //     cloud BEFORE the old client is torn down - CloudPodWatcher#onClose() only removes
                //     itself from the watchers map and does not self-heal/reconnect on its own.
                if (cloud != null) {
                    Reaper.getInstance().watchCloud(cloud);
                }

                // (2) Derive the grace period from THIS cloud's own connect/read timeouts. A slow/hanging
                //     cluster (dead or misconfigured cloud) is exactly the scenario most likely to still
                //     have an in-flight call running close to its configured timeout ceiling, and exactly
                //     the scenario most likely to be evicted/retried repeatedly - so it must not be
                //     under-protected by a flat constant.
                long gracePeriod = cloud != null
                        ? Math.max(cloud.getConnectTimeout(), cloud.getReadTimeout()) + CLOSE_GRACE_MARGIN_SECONDS
                        : DEFAULT_CLOSE_GRACE_PERIOD_SECONDS;

                // (3) Defer the actual close by wall-clock delay (deterministic, NOT
                //     GC/phantom-reachability dependent - cannot repeat the java.lang.ref.Cleaner
                //     self-reference bug from the JENKINS-76095 PR #1747 attempt).
                CLOSE_EXECUTOR.schedule(client.client::close, gracePeriod, TimeUnit.SECONDS);
            })
            .build();

    private KubernetesClientProvider() {}

    static KubernetesClient createClient(KubernetesCloud cloud) throws KubernetesAuthException, IOException {
        String displayName = cloud.getDisplayName();
        final Client c = clients.getIfPresent(displayName);
        if (c == null) {
            KubernetesClient client = new KubernetesFactoryAdapter(
                            cloud.getServerUrl(),
                            cloud.getNamespace(),
                            cloud.getServerCertificate(),
                            cloud.getCredentialsId(),
                            cloud.isSkipTlsVerify(),
                            cloud.getConnectTimeout(),
                            cloud.getReadTimeout(),
                            cloud.getMaxRequestsPerHost(),
                            cloud.isUseJenkinsProxy())
                    .createClient();
            clients.put(displayName, new Client(getValidity(cloud), client));
            LOGGER.log(Level.FINE, "Created new Kubernetes client: {0} {1}", new Object[] {displayName, client});
            return client;
        }
        return c.getClient();
    }

    /**
     * Compute the hash of connection properties of the given cloud. This hash can be used to determine if a cloud
     * was updated and a new connection is needed.
     * @param cloud cloud to compute validity hash for
     * @return client validity hash code
     */
    @Restricted(NoExternalUse.class)
    public static int getValidity(@NonNull KubernetesCloud cloud) {
        Object[] cloudObjects = {
            cloud.getServerUrl(),
            cloud.getNamespace(),
            cloud.getServerCertificate(),
            cloud.getCredentialsId(),
            cloud.isSkipTlsVerify(),
            cloud.getConnectTimeout(),
            cloud.getReadTimeout(),
            cloud.getMaxRequestsPerHostStr(),
            cloud.isUseJenkinsProxy()
        };
        return Arrays.hashCode(cloudObjects);
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

    @Restricted(NoExternalUse.class) // testing only
    public static void invalidate(String displayName) {
        clients.invalidate(displayName);
    }

    /**
     * Look up the {@link KubernetesClient} instance currently held in the cache for the given cloud, without
     * creating one if absent and without affecting the cache's {@code expireAfterWrite} timer.
     *
     * <p>Used by {@link Reaper} to tell whether an existing {@link Reaper} watch is still bound to the
     * <em>same live client instance</em> presently in the cache, as opposed to a client that has since been
     * evicted/replaced (e.g. by expiry, {@link #invalidate}, or a config change) while the cloud's own
     * configuration - and therefore its {@link #getValidity} hash - stayed unchanged. See JENKINS-76095.
     *
     * @param displayName cloud display name (== {@link KubernetesCloud#name})
     * @return the currently cached client for this cloud, or {@code null} if none is cached right now
     */
    @Restricted(NoExternalUse.class)
    @CheckForNull
    public static KubernetesClient currentCachedClient(String displayName) {
        Client c = clients.getIfPresent(displayName);
        return c == null ? null : c.getClient();
    }

    @Restricted(NoExternalUse.class) // testing only
    public static void invalidateAll() {
        clients.invalidateAll();
    }

    // set ordinal to 1 so it runs ahead of Reaper
    @Extension(ordinal = 1)
    public static class SaveableListenerImpl extends SaveableListener {
        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Jenkins) {
                Jenkins jenkins = (Jenkins) o;
                Set<String> cloudDisplayNames = new HashSet<>(clients.asMap().keySet());
                for (KubernetesCloud cloud : jenkins.clouds.getAll(KubernetesCloud.class)) {
                    String displayName = cloud.getDisplayName();
                    Client client = clients.getIfPresent(displayName);
                    if (client == null || client.getValidity() == getValidity(cloud)) {
                        cloudDisplayNames.remove(displayName);
                    }
                }
                // Remove missing / invalid clients
                for (String displayName : cloudDisplayNames) {
                    LOGGER.log(
                            Level.INFO,
                            () -> "Invalidating Kubernetes client: " + displayName + clients.getIfPresent(displayName));
                    invalidate(displayName);
                }
            }
            super.onChange(o, file);
        }
    }
}
