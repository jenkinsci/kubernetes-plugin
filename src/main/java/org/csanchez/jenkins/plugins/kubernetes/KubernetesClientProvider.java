package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.Extension;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;

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
            KubernetesClientProvider.class.getPackage().getName() + ".clients.cacheExpiration", TimeUnit.MINUTES.toSeconds(10));

    private static final Cache<String, Client> clients = Caffeine.newBuilder()
            .expireAfterWrite(CACHE_EXPIRATION, TimeUnit.SECONDS)
            .removalListener( (key, value, cause) -> {
                Client client = (Client) value;
                if (client != null) {
                    LOGGER.log(Level.FINE, () -> "Expiring Kubernetes client " + key + " " + client.client + ": " + cause);
                }
            } )
            .build();

    private KubernetesClientProvider() {
    }

    static KubernetesClient createClient(KubernetesCloud cloud) throws KubernetesAuthException, IOException {
        String displayName = cloud.getDisplayName();
        final Client c = clients.getIfPresent(displayName);
        if (c == null) {
            KubernetesClient client = new KubernetesFactoryAdapter(cloud.getServerUrl(), cloud.getNamespace(),
                    cloud.getServerCertificate(), cloud.getCredentialsId(), cloud.isSkipTlsVerify(),
                    cloud.getConnectTimeout(), cloud.getReadTimeout(), cloud.getMaxRequestsPerHost(), cloud.isUseJenkinsProxy()).createClient();
            clients.put(displayName, new Client(getValidity(cloud), client));
            LOGGER.log(Level.FINE, "Created new Kubernetes client: {0} {1}", new Object[] { displayName, client });
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
        Object[] cloudObjects = { cloud.getServerUrl(), cloud.getNamespace(), cloud.getServerCertificate(),
                cloud.getCredentialsId(), cloud.isSkipTlsVerify(), cloud.getConnectTimeout(), cloud.getReadTimeout(),
                cloud.getMaxRequestsPerHostStr(), cloud.isUseJenkinsProxy() };
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
                    LOGGER.log(Level.INFO, () -> "Invalidating Kubernetes client: " + displayName + clients.getIfPresent(displayName));
                    invalidate(displayName);
                }
            }
            super.onChange(o, file);
        }
    }
}
