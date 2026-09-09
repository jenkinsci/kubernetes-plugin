/*
 * Copyright 2019 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.XmlFile;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.slaves.ComputerListener;
import hudson.slaves.EphemeralNode;
import hudson.slaves.OfflineCause;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.Listeners;
import jenkins.util.SystemProperties;
import jenkins.util.Timer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesClientProvider;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodUtils;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Checks for deleted pods corresponding to {@link KubernetesSlave} and ensures the node is removed from Jenkins too.
 * <p>If the pod has been deleted, all of the associated state (running user processes, workspace, etc.) must also be gone;
 * so there is no point in retaining this agent definition any further.
 * ({@link KubernetesSlave} is not an {@link EphemeralNode}: it <em>does</em> support running across Jenkins restarts.)
 * <p>Note that pod retention policies other than the default {@link Never} may disable this system,
 * unless some external process or garbage collection policy results in pod deletion.
 */
@Extension
public class Reaper extends ComputerListener {

    private static final Logger LOGGER = Logger.getLogger(Reaper.class.getName());

    /**
     * Per-attempt watch-setup timeout, covering BOTH {@link KubernetesCloud#connect()} (client
     * construction/handshake, which can itself block against a slow-but-reachable cloud) and the actual
     * {@code .watch()} call, before treating the attempt as a failure. fabric8's {@code BaseOperation#watch}
     * internally calls {@code Utils.waitUntilReadyOrFail(startedFuture, -1, TimeUnit.SECONDS)} - an
     * INTENTIONAL, hardcoded indefinite wait with no way for a caller to override it. Against a cloud that
     * accepts a connection but never completes the watch handshake (a "hanging" endpoint, or one merely slow
     * under heavy concurrent load), that indefinite wait blocks the calling thread forever inside
     * {@code ForkJoinPool.commonPool()}'s managed-blocking/compensation machinery. Since that pool is
     * JVM-wide and shared by every unrelated async operation (not just other clouds' watches), enough
     * concurrent stuck attempts against ONE bad cloud can exhaust its bounded compensation-thread budget and
     * collaterally starve watch setup for every OTHER, perfectly healthy cloud too. Running the watch-ready
     * wait on {@link #WATCH_SETUP_EXECUTOR} instead confines this risk to a small, dedicated, bounded pool
     * so a hanging cloud can no longer poison JVM-wide shared infrastructure. See JENKINS-76095 for details.
     *
     * <p>By default (when this system property is unset or {@code <= 0}), the bound used is derived
     * per-cloud from settings the operator has ALREADY configured for exactly this purpose - {@link
     * KubernetesCloud#getConnectTimeout()} {@code +} {@link KubernetesCloud#getReadTimeout()} - rather than
     * one hardcoded JVM-wide constant. connect-then-watch is a strictly sequential operation (the watch
     * cannot begin until the connection completes), so the worst-case total wait is the SUM of both
     * timeouts, not their max. This means a cloud already configured as "known to be slower" (higher
     * connect/read timeouts) automatically gets a proportionally more patient watch-setup bound too, with no
     * separate setting for an operator to remember to keep in sync. See {@link #watchSetupTimeoutSeconds}.
     *
     * <p>This system property remains available as an explicit, fixed override for environments that want a
     * single hardcoded bound regardless of any individual cloud's configured connect/read timeouts.
     *
     * <p><b>Note:</b> like all {@code static final} fields on this class read via {@link SystemProperties},
     * this value is resolved exactly once, when the {@code Reaper} class is first loaded (effectively at
     * Jenkins startup). Setting this system property at runtime (e.g. via the Script Console's
     * {@code System.setProperty(...)}) has no effect on an already-running instance - it must be set as a
     * JVM startup argument (e.g. {@code -Dorg.csanchez.jenkins.plugins.kubernetes.pod.retention.Reaper.watchSetupTimeoutSecondsOverride=30})
     * before Jenkins starts, and Jenkins must be (re)started for a change to take effect.
     */
    private static final int WATCH_SETUP_TIMEOUT_SECONDS_OVERRIDE =
            SystemProperties.getInteger(Reaper.class.getName() + ".watchSetupTimeoutSecondsOverride", 0);

    /**
     * Returns the watch-setup timeout (seconds) to use for a single attempt against the given cloud - the
     * fixed {@link #WATCH_SETUP_TIMEOUT_SECONDS_OVERRIDE} if explicitly set to a positive value, otherwise
     * derived per-cloud as {@code connectTimeout + readTimeout}. See {@link #WATCH_SETUP_TIMEOUT_SECONDS_OVERRIDE}
     * for the full rationale.
     */
    private static int watchSetupTimeoutSecondsFor(@NonNull KubernetesCloud kc) {
        if (WATCH_SETUP_TIMEOUT_SECONDS_OVERRIDE > 0) {
            return WATCH_SETUP_TIMEOUT_SECONDS_OVERRIDE;
        }
        return kc.getConnectTimeout() + kc.getReadTimeout();
    }

    /**
     * Upper bound on concurrently in-flight watch-setup attempts across all clouds. Deliberately bounded
     * (not a cached/unbounded pool) - if this cap is reached, further attempts are rejected immediately
     * (surfacing as a {@link RejectedExecutionException}, handled identically to any other watch-setup
     * failure by {@link #watchCloud} and folded into the existing failure-streak/throttle mechanism) rather
     * than piling up additional threads. A stuck attempt that timed out here is abandoned (best-effort
     * {@link Future#cancel}, which - like the underlying fabric8/OkHttp call - cannot forcibly interrupt a
     * blocked read) but is now permanently confined to this small dedicated pool instead of the shared
     * {@code ForkJoinPool.commonPool()}.
     *
     * <p>Rather than a single hardcoded constant (which would either be wastefully large for a small
     * Jenkins instance or too small for one with many configured {@link KubernetesCloud}s - there is no
     * one-size-fits-all number), the cap is derived from the actual number of currently configured clouds,
     * multiplied by {@link #WATCH_SETUP_THREADS_PER_CLOUD} headroom so a burst where every cloud needs
     * re-arming at once (e.g. right after a Jenkins restart, or many concurrent client-cache evictions) is
     * never artificially bottlenecked by this pool itself, with a small floor for the common case of very
     * few clouds. Recomputed via {@link #resizeWatchSetupExecutor} every time {@link #watchClouds()} runs
     * (i.e. whenever the set of configured clouds is (re-)enumerated), so it grows/shrinks automatically as
     * clouds are added or removed at runtime - operators never need to hand-tune this for their
     * environment's scale, and no restart is needed for the pool itself to track a changed cloud count.
     * An explicit override remains available via the {@code watchSetupMaxThreads} system property for
     * environments that want a fixed cap regardless of cloud count.
     *
     * <p><b>Note:</b> {@link #WATCH_SETUP_MIN_THREADS}, {@link #WATCH_SETUP_THREADS_PER_CLOUD}, and
     * {@link #WATCH_SETUP_MAX_THREADS_OVERRIDE} are themselves {@code static final} and, like
     * {@link #WATCH_SETUP_TIMEOUT_SECONDS_OVERRIDE} above, are resolved once at class-load time - changing their
     * underlying system properties requires a JVM startup argument and a Jenkins (re)start to take effect.
     * Only the *pool size itself* (derived from these settings plus the live cloud count) updates
     * dynamically without a restart, not the settings that control how it's derived.
     */
    private static final int WATCH_SETUP_MIN_THREADS =
            SystemProperties.getInteger(Reaper.class.getName() + ".watchSetupMinThreads", 20);

    /** Headroom multiplier applied per configured cloud when auto-sizing {@link #WATCH_SETUP_EXECUTOR}. */
    private static final int WATCH_SETUP_THREADS_PER_CLOUD =
            SystemProperties.getInteger(Reaper.class.getName() + ".watchSetupThreadsPerCloud", 4);

    /**
     * Explicit fixed override for the pool's max size, bypassing auto-sizing entirely when set to a
     * positive value. Unset (0, the default) means "auto-size based on configured cloud count".
     */
    private static final int WATCH_SETUP_MAX_THREADS_OVERRIDE =
            SystemProperties.getInteger(Reaper.class.getName() + ".watchSetupMaxThreads", 0);

    private static final ExecutorService WATCH_SETUP_EXECUTOR = new ThreadPoolExecutor(
            0,
            WATCH_SETUP_MIN_THREADS,
            60L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new NamingThreadFactory(new DaemonThreadFactory(), Reaper.class.getName() + ".watchSetup"));

    /**
     * Grows (or shrinks) {@link #WATCH_SETUP_EXECUTOR}'s max pool size to track the current number of
     * configured {@link KubernetesCloud}s, so the pool never becomes an artificial bottleneck as an
     * installation's cloud count grows, and never stays needlessly oversized after clouds are removed. See
     * the {@link #WATCH_SETUP_MIN_THREADS} javadoc for the rationale.
     */
    private static void resizeWatchSetupExecutor(int currentCloudCount) {
        int desired = WATCH_SETUP_MAX_THREADS_OVERRIDE > 0
                ? WATCH_SETUP_MAX_THREADS_OVERRIDE
                : Math.max(WATCH_SETUP_MIN_THREADS, currentCloudCount * WATCH_SETUP_THREADS_PER_CLOUD);
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) WATCH_SETUP_EXECUTOR;
        if (tpe.getMaximumPoolSize() != desired) {
            tpe.setMaximumPoolSize(desired);
            LOGGER.fine(() -> "resized watch-setup executor max pool size to " + desired + " for " + currentCloudCount
                    + " configured cloud(s)");
        }
    }

    /**
     * Only useful for tests which shutdown Jenkins without terminating the JVM.
     * Close the watch so that we don't end up with spam in logs
     */
    @Extension
    public static class ReaperShutdownListener extends ItemListener {
        @Override
        public void onBeforeShutdown() {
            Reaper.getInstance().closeAllWatchers();
        }
    }

    public static Reaper getInstance() {
        return ExtensionList.lookupSingleton(Reaper.class);
    }

    /**
     * Activate this feature only if and when some Kubernetes agent is actually used.
     * Avoids touching the API server when this plugin is not even in use.
     */
    private final AtomicBoolean activated = new AtomicBoolean();

    private final Map<String, CloudPodWatcher> watchers = new ConcurrentHashMap<>();

    /**
     * Throttles how often {@link #watchCloud} will retry establishing the pod-event watch for a given cloud
     * <em>after {@link #MIN_CONSECUTIVE_FAILURES_BEFORE_THROTTLE} or more consecutive connect/watch attempts
     * for that exact cloud configuration have failed</em>. Without this, rapid repeated cache
     * eviction/invalidation of a persistently unreachable cloud's {@link KubernetesClient} (e.g. many
     * concurrent job/pod launches all hitting a broken cloud) can drive {@link #watchCloud} to attempt a
     * fresh connect-and-watch on every single eviction. Each such attempt spins up its own fabric8
     * {@code AbstractWatchManager} reconnect-retry thread(s) against the dead endpoint, and under high
     * eviction rates these accumulate faster than they can be drained/closed, leading to unbounded thread
     * growth and eventual JVM crash - even though JENKINS-76095's proactive re-arm is otherwise working
     * exactly as intended.
     * <p>Keyed by {@code cloudName + ":" + clientValidity} (not just cloud name), so this deliberately does
     * NOT throttle legitimate immediate reconnects: a cloud config change (different {@code clientValidity}),
     * a watch closed via {@code HTTP_GONE}, or a new computer launching on an otherwise-healthy cloud all
     * proceed immediately since no failure streak is recorded for that exact key.
     * <p>Requiring a short streak of consecutive failures (rather than throttling on the very first failure)
     * intentionally tolerates real-world transient/intermittent connectivity blips (a single dropped
     * connection, brief DNS hiccup, API server restart, etc.) without delaying recovery - only a
     * <em>sustained</em> run of failures against the same config is treated as "this cloud is genuinely
     * down right now" and gets backed off. Once throttling engages, the floor between retries is
     * configurable per-cloud via {@link KubernetesCloud#getMinWatchRetryIntervalSeconds()} (UI field
     * "Minimum Watch Retry Interval"), defaulting to
     * {@link KubernetesCloud#DEFAULT_MIN_WATCH_RETRY_INTERVAL_SECONDS} (10s) if unset/unconfigured. Any
     * success resets the streak immediately, so throttling never outlives an actual recovery.
     */
    private static final int MIN_CONSECUTIVE_FAILURES_BEFORE_THROTTLE =
            SystemProperties.getInteger(Reaper.class.getName() + ".minConsecutiveFailuresBeforeThrottle", 3);

    /** Per-{@code cloudName:clientValidity} consecutive-failure streak state. */
    private static final class FailureStreak {
        private final AtomicInteger count = new AtomicInteger();
        private volatile long lastFailureMs;
    }

    private final Map<String, FailureStreak> watchFailureStreaks = new ConcurrentHashMap<>();

    private final LoadingCache<String, Set<String>> terminationReasons =
            Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.DAYS).build(k -> new ConcurrentSkipListSet<>());

    @Override
    public void preLaunch(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
        if (c instanceof KubernetesComputer) {
            Timer.get().schedule(this::maybeActivate, 10, TimeUnit.SECONDS);

            // ensure associated cloud is being watched. the watch may have been closed due to exception or
            // failure to register on initial activation.
            KubernetesSlave node = ((KubernetesComputer) c).getNode();
            if (node != null && !isWatchingCloud(node.getCloudName())) {
                try {
                    watchCloud(node.getKubernetesCloud());
                } catch (IllegalStateException ise) {
                    LOGGER.log(Level.WARNING, ise, () -> "kubernetes cloud not found: " + node.getCloudName());
                }
            }
        }
    }

    public void maybeActivate() {
        if (activated.compareAndSet(false, true)) {
            activate();
        }
    }

    private void activate() {
        LOGGER.fine("Activating reaper");
        // First check all existing nodes to see if they still have active pods.
        // (We may have missed deletion events while Jenkins was shut off,
        // or pods may have been deleted before any Kubernetes agent was brought online.)
        reapAgents();

        // Now set up a watch for any subsequent pod deletions.
        watchClouds();
    }

    /**
     * Remove any {@link KubernetesSlave} nodes that reference Pods that don't exist.
     */
    private void reapAgents() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            for (Node n : new ArrayList<>(jenkins.getNodes())) {
                if (!(n instanceof KubernetesSlave)) {
                    continue;
                }
                KubernetesSlave ks = (KubernetesSlave) n;
                if (ks.getLauncher().isLaunchSupported()) {
                    // Being launched, don't touch it.
                    continue;
                }
                String ns = ks.getNamespace();
                String name = ks.getPodName();
                try {
                    // TODO more efficient to do a single (or paged) list request, but tricky since there may be
                    // multiple clouds,
                    // and even within a single cloud an agent pod is permitted to use a nondefault namespace,
                    // yet we do not want to do an unnamespaced pod list for RBAC reasons.
                    // Could use a hybrid approach: first list all pods in the configured namespace for all clouds;
                    // then go back and individually check any unmatched agents with their configured namespace.
                    KubernetesCloud cloud = ks.getKubernetesCloud();
                    if (cloud.connect().pods().inNamespace(ns).withName(name).get() == null) {
                        LOGGER.info(() -> ns + "/" + name
                                + " seems to have been deleted, so removing corresponding Jenkins agent");
                        jenkins.removeNode(ks);
                    } else {
                        LOGGER.fine(() -> ns + "/" + name + " still seems to exist, OK");
                    }
                } catch (KubernetesAuthException | IOException | RuntimeException x) {
                    LOGGER.log(Level.WARNING, x, () -> "failed to do initial reap check for " + ns + "/" + name);
                }
            }
        }
    }

    /**
     * Create watchers for each configured {@link KubernetesCloud} in Jenkins and remove any existing watchers
     * for clouds that have been removed. If a {@link KubernetesCloud} client configuration property has been
     * updated a new watcher will be created to replace the existing one.
     */
    private void watchClouds() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            List<KubernetesCloud> clouds = jenkins.clouds.getAll(KubernetesCloud.class);
            Set<String> cloudNames = new HashSet<>(this.watchers.keySet());
            for (KubernetesCloud kc : clouds) {
                watchCloud(kc);
                cloudNames.remove(kc.name);
            }

            // close any cloud watchers that have been removed
            cloudNames.stream().map(this.watchers::get).filter(Objects::nonNull).forEach(cpw -> {
                LOGGER.info(() -> "stopping pod watcher for deleted kubernetes cloud " + cpw.cloudName);
                cpw.stop();
            });
        }
    }

    /**
     * Register {@link CloudPodWatcher} for the given cloud if one does not exist or if the existing watcher
     * is no longer valid.
     *
     * <p>Package-visibility is widened (was {@code private}) so that {@link KubernetesClientProvider} can
     * proactively re-establish this cloud's watch on a fresh client immediately when its cached client is
     * being retired, instead of relying solely on the next Jenkins config-save/agent-connect event to notice
     * a dropped watch. See JENKINS-76095.
     * @param kc kubernetes cloud to watch
     */
    @Restricted(NoExternalUse.class)
    public void watchCloud(@NonNull KubernetesCloud kc) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            resizeWatchSetupExecutor(
                    jenkins.clouds.getAll(KubernetesCloud.class).size());
        }
        // can't use ConcurrentHashMap#computeIfAbsent because CloudPodWatcher will remove itself from the watchers
        // map on close. If an error occurs when creating the watch it would create a deadlock situation.
        CloudPodWatcher watcher = new CloudPodWatcher(kc);
        if (!isCloudPodWatcherActive(watcher)) {
            String failureKey = kc.name + ":" + watcher.clientValidity;
            long minRetryIntervalMs = kc.getMinWatchRetryIntervalSeconds() * 1000L;
            if (minRetryIntervalMs > 0 && !retryAllowed(failureKey, minRetryIntervalMs)) {
                return;
            }
            try {
                // Both kc.connect() (which may itself perform a blocking handshake/version-check against
                // the cloud's API server - e.g. building a fresh client after cache eviction) AND the
                // actual .watch() call are submitted together as a SINGLE task bounded by
                // WATCH_SETUP_EXECUTOR, bounded by watchSetupTimeoutSecondsFor(kc). A cloud that is genuinely reachable
                // but merely slow to respond (heavy load) can block INSIDE connect() just as easily as
                // inside watch() - if only the watch() call were bounded, a slow-but-alive cloud could
                // still tie up whichever thread called watchCloud() (e.g. a ForkJoinPool.commonPool()
                // worker via KubernetesClientProvider's Caffeine eviction listener) for as long as
                // connect() takes, silently reopening the JENKINS-76095 starvation risk through a
                // different, unbounded code path. See JENKINS-76095.
                int timeoutSeconds = watchSetupTimeoutSecondsFor(kc);
                KubernetesClient[] connectedClient = new KubernetesClient[1];
                Future<Watch> watchFuture = WATCH_SETUP_EXECUTOR.submit(() -> {
                    KubernetesClient client = kc.connect();
                    connectedClient[0] = client;
                    return client.pods().inNamespace(client.getNamespace()).watch(watcher);
                });
                Watch established;
                try {
                    established = watchFuture.get(timeoutSeconds, TimeUnit.SECONDS);
                } catch (TimeoutException te) {
                    watchFuture.cancel(true);
                    throw new IOException(
                            "timed out after "
                                    + timeoutSeconds
                                    + "s waiting to connect and set up watcher on "
                                    + kc.getDisplayName(),
                            te);
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                    if (cause instanceof KubernetesAuthException) {
                        throw (KubernetesAuthException) cause;
                    }
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    }
                    throw new IOException(cause);
                } catch (InterruptedException ie) {
                    watchFuture.cancel(true);
                    Thread.currentThread().interrupt();
                    throw new IOException(
                            "interrupted while waiting to connect and set up watcher on " + kc.getDisplayName(), ie);
                }
                watcher.watch = established;
                watcher.boundClient = connectedClient[0];
                CloudPodWatcher old = watchers.put(kc.name, watcher);
                // if another watch slipped in then make sure it stopped
                if (old != null) {
                    old.stop();
                }
                // success: clear any failure streak so a later, unrelated future failure isn't throttled
                // based on stale history
                watchFailureStreaks.remove(failureKey);
                LOGGER.info(() -> "set up watcher on " + kc.getDisplayName());
            } catch (KubernetesAuthException | IOException | RuntimeException x) {
                FailureStreak streak = watchFailureStreaks.computeIfAbsent(failureKey, k -> new FailureStreak());
                streak.count.incrementAndGet();
                streak.lastFailureMs = System.currentTimeMillis();
                LOGGER.log(Level.WARNING, x, () -> "failed to set up watcher on " + kc.getDisplayName());
            }
        }
    }

    /**
     * Best-effort throttle: for a given {@code cloudName:clientValidity} key, returns false only once at
     * least {@link #MIN_CONSECUTIVE_FAILURES_BEFORE_THROTTLE} consecutive failures have been recorded AND
     * the most recent one happened within {@code minIntervalMs}. A short streak of failures (below the
     * threshold) is always allowed to retry immediately, tolerating transient/intermittent connectivity
     * issues without delay - only a sustained failure run backs off. Returns true (allowing the attempt) if
     * there's no failure streak recorded for this key at all - i.e. this is either the first attempt, or the
     * previous attempt for this exact config succeeded (see the {@code watchFailureStreaks.remove} call in
     * {@link #watchCloud}) - so legitimate immediate reconnects (config change, watch closed via
     * {@code HTTP_GONE}, new computer launch on a healthy cloud) are never throttled.
     */
    private boolean retryAllowed(@NonNull String failureKey, long minIntervalMs) {
        FailureStreak streak = watchFailureStreaks.get(failureKey);
        if (streak == null || streak.count.get() < MIN_CONSECUTIVE_FAILURES_BEFORE_THROTTLE) {
            return true;
        }
        return System.currentTimeMillis() - streak.lastFailureMs >= minIntervalMs;
    }

    /**
     * Check if the cloud is watched for Pod events.
     * @param name cloud name
     * @return true if a watcher has been registered for the given cloud
     */
    boolean isWatchingCloud(String name) {
        return watchers.get(name) != null;
    }

    public Map<String, ?> getWatchers() {
        return watchers;
    }

    /**
     * Check if the given cloud pod watcher exists and is still valid. Watchers may become invalid
     * of the kubernetes client configuration changes.
     * @param watcher watcher to check
     * @return true if the provided watcher already exists and is valid, false otherwise
     */
    private boolean isCloudPodWatcherActive(@NonNull CloudPodWatcher watcher) {
        CloudPodWatcher existing = watchers.get(watcher.cloudName);
        if (existing == null || existing.clientValidity != watcher.clientValidity) {
            return false;
        }
        // Config unchanged, but the existing watch may still be bound to a KubernetesClient instance that
        // KubernetesClientProvider has since evicted/replaced (cache expiry, explicit invalidate(), etc.)
        // without the watch itself having failed/closed yet - e.g. it's sitting on the deferred
        // grace-period-close timer. Comparing only the config-derived clientValidity can't detect this, so a
        // watch bound to a retired client instance would be wrongly reported as still active, silently
        // defeating JENKINS-76095's proactive re-arm (which relies on this method reporting false so a fresh
        // watch gets established on the live client). Confirm the watch is actually still riding the client
        // instance presently held in KubernetesClientProvider's cache before calling it active.
        KubernetesClient currentClient = KubernetesClientProvider.currentCachedClient(watcher.cloudName);
        return existing.boundClient != null && existing.boundClient == currentClient;
    }

    private static Optional<KubernetesSlave> resolveNode(@NonNull Jenkins jenkins, String namespace, String name) {
        return new ArrayList<>(jenkins.getNodes())
                .stream()
                        .filter(KubernetesSlave.class::isInstance)
                        .map(KubernetesSlave.class::cast)
                        .filter(ks ->
                                Objects.equals(ks.getNamespace(), namespace) && Objects.equals(ks.getPodName(), name))
                        .findFirst();
    }

    /**
     * Stop all watchers
     */
    private void closeAllWatchers() {
        // on close each watcher should remove itself from the watchers map (see CloudPodWatcher#onClose)
        watchers.values().forEach(CloudPodWatcher::stop);
    }

    /**
     * Kubernetes pod event watcher for a Kubernetes Cloud. Notifies {@link Listener}
     * extensions on Pod events. The default Kubernetes client watch manager will
     * attempt to reconnect on connection errors. If the watch api returns "410 Gone"
     * then the Watch will close itself with a WatchException and this watcher will
     * deregister itself.
     */
    private class CloudPodWatcher implements Watcher<Pod> {
        private final String cloudName;
        private final int clientValidity;

        @CheckForNull
        private Watch watch;

        /**
         * The {@link KubernetesClient} instance this watch is actually registered against, captured once the
         * watch is successfully established. Used (instead of relying on {@link #clientValidity} alone) to
         * detect that the underlying client has since been evicted/replaced by {@link KubernetesClientProvider}
         * even though the cloud's configuration - and therefore {@link #clientValidity} - is unchanged. See
         * JENKINS-76095.
         */
        @CheckForNull
        private volatile KubernetesClient boundClient;

        CloudPodWatcher(@NonNull KubernetesCloud cloud) {
            this.cloudName = cloud.name;
            this.clientValidity = KubernetesClientProvider.getValidity(cloud);
        }

        @Override
        public void eventReceived(Action action, Pod pod) {
            // don't send bookmark event to listeners as they don't represent change in pod state
            if (action == Action.BOOKMARK) {
                // TODO future enhancement might be to keep track of bookmarks for better reconnect behavior. Would
                //      likely have to track based on cloud address/namespace in case cloud was renamed or namespace
                //      is changed.
                return;
            }

            // If there was a non-success http response code from watch request
            // or the api returned a Status object the watch manager notifies with
            // an error action and null resource.
            if (action == Action.ERROR && pod == null) {
                return;
            }

            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return;
            }

            String ns = pod.getMetadata().getNamespace();
            String name = pod.getMetadata().getName();
            Optional<KubernetesSlave> optionalNode = resolveNode(jenkins, ns, name);
            if (!optionalNode.isPresent()) {
                return;
            }

            Listeners.notify(Listener.class, true, listener -> {
                try {
                    Set<String> terminationReasons = Reaper.this.terminationReasons.get(
                            optionalNode.get().getNodeName());
                    listener.onEvent(
                            action,
                            optionalNode.get(),
                            pod,
                            terminationReasons != null ? terminationReasons : Collections.emptySet());
                } catch (Exception x) {
                    LOGGER.log(Level.WARNING, "Listener " + listener + " failed for " + ns + "/" + name, x);
                }
            });
        }

        /**
         * Close the associated {@link Watch} handle. This should be used shutdown/stop the watch. It will cause the
         * watch manager to call this classes {@link #onClose()} method.
         */
        void stop() {
            if (watch != null) {
                LOGGER.info("Stopping watch for kubernetes cloud " + cloudName);
                this.watch.close();
            }
        }

        @Override
        public void onClose() {
            LOGGER.fine(() -> cloudName + " watcher closed");
            // remove self from watchers list
            Reaper.this.watchers.remove(cloudName, this);
        }

        @Override
        public void onClose(WatcherException e) {
            // usually triggered because of "410 Gone" responses
            // https://kubernetes.io/docs/reference/using-api/api-concepts/#410-gone-responses
            // "Gone" may be returned if the resource version requested is older than the server
            // has retained.
            LOGGER.log(Level.WARNING, e, () -> cloudName + " watcher closed with exception");
            // remove self from watchers list
            Reaper.this.watchers.remove(cloudName, this);
        }
    }

    /**
     * Get any reason(s) why a node was terminated by a listener.
     * @param node a {@link Node#getNodeName}
     * @return a possibly empty set of {@link ContainerStateTerminated#getReason} or {@link PodStatus#getReason}
     */
    @NonNull
    public Set<String> terminationReasons(@NonNull String node) {
        synchronized (terminationReasons) {
            return new HashSet<>(terminationReasons.get(node));
        }
    }

    /**
     * Listener called when a Kubernetes event related to a Kubernetes agent happens.
     */
    public interface Listener extends ExtensionPoint {

        /**
         * Handle Pod event.
         * @param action the kind of event that happened to the referred pod
         * @param node The affected node
         * @param pod The affected pod
         * @param terminationReasons Set of termination reasons
         */
        void onEvent(
                @NonNull Watcher.Action action,
                @NonNull KubernetesSlave node,
                @NonNull Pod pod,
                @NonNull Set<String> terminationReasons)
                throws IOException, InterruptedException;
    }

    @Extension
    public static class RemoveAgentOnPodDeleted implements Listener {
        @Override
        public void onEvent(
                @NonNull Watcher.Action action,
                @NonNull KubernetesSlave node,
                @NonNull Pod pod,
                @NonNull Set<String> terminationReasons)
                throws IOException {
            if (action != Watcher.Action.DELETED) {
                return;
            }
            String ns = pod.getMetadata().getNamespace();
            String name = pod.getMetadata().getName();
            LOGGER.info(() -> ns + "/" + name + " was just deleted, so removing corresponding Jenkins agent");
            node.getRunListener().getLogger().printf("Pod %s/%s was just deleted%n", ns, name);
            Jenkins.get().removeNode(node);
            disconnectComputer(node, new PodOfflineCause(Messages._PodOfflineCause_PodDeleted()));
        }
    }

    @Extension
    public static class TerminateAgentOnContainerTerminated implements Listener {

        @Override
        public void onEvent(
                @NonNull Watcher.Action action,
                @NonNull KubernetesSlave node,
                @NonNull Pod pod,
                @NonNull Set<String> terminationReasons)
                throws IOException, InterruptedException {
            if (action != Watcher.Action.MODIFIED) {
                return;
            }

            List<ContainerStatus> terminatedContainers = PodUtils.getTerminatedContainers(pod);
            if (!terminatedContainers.isEmpty()) {
                List<String> containers = new ArrayList<>();
                terminatedContainers.forEach(c -> {
                    ContainerStateTerminated t = c.getState().getTerminated();
                    String containerName = c.getName();
                    containers.add(containerName);
                    String reason = t.getReason();
                    if (reason != null) {
                        terminationReasons.add(reason);
                    }
                });
                String reason = pod.getStatus().getReason();
                String message = pod.getStatus().getMessage();
                var sb = new StringBuilder()
                        .append(pod.getMetadata().getNamespace())
                        .append("/")
                        .append(pod.getMetadata().getName());
                if (containers.size() > 1) {
                    sb.append(" Containers ")
                            .append(String.join(",", containers))
                            .append(" were terminated.");
                } else {
                    sb.append(" Container ")
                            .append(String.join(",", containers))
                            .append(" was terminated.");
                }
                logAndCleanUp(
                        node,
                        pod,
                        terminationReasons,
                        reason,
                        message,
                        sb,
                        node.getRunListener(),
                        new PodOfflineCause(Messages._PodOfflineCause_ContainerFailed("ContainerError", containers)));
            }
        }
    }

    @Extension
    public static class TerminateAgentOnPodFailed implements Listener {
        @Override
        public void onEvent(
                @NonNull Watcher.Action action,
                @NonNull KubernetesSlave node,
                @NonNull Pod pod,
                @NonNull Set<String> terminationReasons)
                throws IOException, InterruptedException {
            if (action != Watcher.Action.MODIFIED) {
                return;
            }

            if ("Failed".equals(pod.getStatus().getPhase())) {
                String reason = pod.getStatus().getReason();
                String message = pod.getStatus().getMessage();
                logAndCleanUp(
                        node,
                        pod,
                        terminationReasons,
                        reason,
                        message,
                        new StringBuilder()
                                .append(pod.getMetadata().getNamespace())
                                .append("/")
                                .append(pod.getMetadata().getName())
                                .append(" Pod just failed."),
                        node.getRunListener(),
                        new PodOfflineCause(Messages._PodOfflineCause_PodFailed(reason, message)));
            }
        }
    }

    private static void logAndCleanUp(
            KubernetesSlave node,
            Pod pod,
            Set<String> terminationReasons,
            String reason,
            String message,
            StringBuilder sb,
            TaskListener runListener,
            PodOfflineCause cause)
            throws IOException, InterruptedException {
        List<String> details = new ArrayList<>();
        if (reason != null) {
            details.add("Reason: " + reason);
            terminationReasons.add(reason);
        }
        if (message != null) {
            details.add("Message: " + message);
        }
        if (!details.isEmpty()) {
            sb.append(" ").append(String.join(", ", details)).append(".");
        }
        var evictionCondition = pod.getStatus().getConditions().stream()
                .filter(c -> "EvictionByEvictionAPI".equals(c.getReason()))
                .findFirst();
        if (evictionCondition.isPresent()) {
            sb.append(" Pod was evicted by the Kubernetes Eviction API.");
            terminationReasons.add(evictionCondition.get().getReason());
        }
        LOGGER.info(() -> sb + " Removing corresponding node " + node.getNodeName() + " from Jenkins.");
        runListener.getLogger().println(sb);
        logLastLinesThenTerminateNode(node, pod, runListener);
        PodUtils.cancelQueueItemFor(pod, "PodFailure");
        disconnectComputer(node, cause);
    }

    private static void logLastLinesThenTerminateNode(KubernetesSlave node, Pod pod, TaskListener runListener)
            throws IOException, InterruptedException {
        try {
            String lines = PodUtils.logLastLines(pod, node.getKubernetesCloud().connect());
            if (lines != null) {
                runListener.getLogger().print(lines);
                String ns = pod.getMetadata().getNamespace();
                String name = pod.getMetadata().getName();
                LOGGER.fine(() -> ns + "/" + name + " log:\n" + lines);
            }
        } catch (KubernetesAuthException e) {
            LOGGER.log(Level.FINE, e, () -> "Unable to get logs after pod failed event");
        } finally {
            node.terminate();
        }
    }

    /**
     * Disconnect computer associated with the given node. Should be called AFTER terminate so the offline cause
     * takes precedence over the one set by {@link KubernetesSlave#terminate()} (via {@link jenkins.model.Nodes#removeNode(Node)}).
     * @see Computer#disconnect(OfflineCause)
     * @param node node to disconnect
     * @param cause reason for offline
     */
    private static void disconnectComputer(KubernetesSlave node, OfflineCause cause) {
        Computer computer = node.getComputer();
        if (computer != null) {
            computer.disconnect(cause);
        }
    }

    @Extension
    public static class TerminateAgentOnImagePullBackOff implements Listener {

        @SuppressFBWarnings(
                value = "MS_SHOULD_BE_FINAL",
                justification = "Allow tests or groovy console to change the value")
        public static long BACKOFF_EVENTS_LIMIT =
                SystemProperties.getInteger(Reaper.class.getName() + ".backoffEventsLimit", 3);

        public static final String IMAGE_PULL_BACK_OFF = "ImagePullBackOff";

        // For each pod with at least 1 backoff, keep track of the first backoff event for 15 minutes.
        private Cache<String, Integer> ttlCache =
                Caffeine.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build();

        @Override
        public void onEvent(
                @NonNull Watcher.Action action,
                @NonNull KubernetesSlave node,
                @NonNull Pod pod,
                @NonNull Set<String> terminationReasons)
                throws IOException, InterruptedException {
            if (action != Watcher.Action.MODIFIED) {
                return;
            }

            List<ContainerStatus> backOffContainers = PodUtils.getContainers(pod, cs -> {
                ContainerStateWaiting waiting = cs.getState().getWaiting();
                return waiting != null
                        && waiting.getMessage() != null
                        && waiting.getMessage().contains("Back-off pulling image");
            });

            if (!backOffContainers.isEmpty()) {
                List<String> images = new ArrayList<>();
                backOffContainers.forEach(cs -> images.add(cs.getImage()));
                var podUid = pod.getMetadata().getUid();
                var backOffNumber = ttlCache.get(podUid, k -> 0);
                ttlCache.put(podUid, ++backOffNumber);
                if (backOffNumber >= BACKOFF_EVENTS_LIMIT) {
                    var imagesString = String.join(",", images);
                    node.getRunListener()
                            .error("Unable to pull container image \"" + imagesString
                                    + "\". Check if image tag name is spelled correctly.");
                    terminationReasons.add(IMAGE_PULL_BACK_OFF);
                    PodUtils.cancelQueueItemFor(pod, IMAGE_PULL_BACK_OFF);
                    node.terminate();
                    disconnectComputer(
                            node,
                            new PodOfflineCause(
                                    Messages._PodOfflineCause_ImagePullBackoff(IMAGE_PULL_BACK_OFF, images)));
                } else {
                    node.getRunListener()
                            .error("Image pull backoff detected, waiting for image to be available. Will wait for "
                                    + (BACKOFF_EVENTS_LIMIT - backOffNumber)
                                    + " more events before terminating the node.");
                }
            }
        }
    }

    /**
     * {@link SaveableListener} that will update cloud watchers when Jenkins configuration is updated.
     */
    @Extension
    public static class ReaperSaveableListener extends SaveableListener {
        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Jenkins) {
                Reaper reaper = Reaper.getInstance();
                // only update if reaper has been activated to avoid hitting api server if not in use
                if (reaper.activated.get()) {
                    Reaper.getInstance().watchClouds();
                }
            }
        }
    }
}
