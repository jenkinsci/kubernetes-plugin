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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
            Set<String> cloudNames = new HashSet<>(this.watchers.keySet());
            for (KubernetesCloud kc : jenkins.clouds.getAll(KubernetesCloud.class)) {
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
     * @param kc kubernetes cloud to watch
     */
    private void watchCloud(@NonNull KubernetesCloud kc) {
        // can't use ConcurrentHashMap#computeIfAbsent because CloudPodWatcher will remove itself from the watchers
        // map on close. If an error occurs when creating the watch it would create a deadlock situation.
        CloudPodWatcher watcher = new CloudPodWatcher(kc);
        if (!isCloudPodWatcherActive(watcher)) {
            try {
                KubernetesClient client = kc.connect();
                watcher.watch = client.pods().inNamespace(client.getNamespace()).watch(watcher);
                CloudPodWatcher old = watchers.put(kc.name, watcher);
                // if another watch slipped in then make sure it stopped
                if (old != null) {
                    old.stop();
                }
                LOGGER.info(() -> "set up watcher on " + kc.getDisplayName());
            } catch (KubernetesAuthException | IOException | RuntimeException x) {
                LOGGER.log(Level.WARNING, x, () -> "failed to set up watcher on " + kc.getDisplayName());
            }
        }
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
        return existing != null && existing.clientValidity == watcher.clientValidity;
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
