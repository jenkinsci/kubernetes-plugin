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
import jenkins.util.Timer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesClientProvider;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
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

    private final LoadingCache<String, Set<String>> terminationReasons = Caffeine.newBuilder().
        expireAfterAccess(1, TimeUnit.DAYS).
        build(k -> new ConcurrentSkipListSet<>());

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
                    // TODO more efficient to do a single (or paged) list request, but tricky since there may be multiple clouds,
                    // and even within a single cloud an agent pod is permitted to use a nondefault namespace,
                    // yet we do not want to do an unnamespaced pod list for RBAC reasons.
                    // Could use a hybrid approach: first list all pods in the configured namespace for all clouds;
                    // then go back and individually check any unmatched agents with their configured namespace.
                    KubernetesCloud cloud = ks.getKubernetesCloud();
                    if (cloud.connect().pods().inNamespace(ns).withName(name).get() == null) {
                        LOGGER.info(() -> ns + "/" + name + " seems to have been deleted, so removing corresponding Jenkins agent");
                        jenkins.removeNode(ks);
                    } else {
                        LOGGER.fine(() -> ns + "/" + name + " still seems to exist, OK");
                    }
                } catch (Exception x) {
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
            cloudNames.stream()
                    .map(this.watchers::get)
                    .filter(Objects::nonNull)
                    .forEach(cpw -> {
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
            } catch (Exception x) {
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
        return new ArrayList<>(jenkins.getNodes()).stream()
                .filter(KubernetesSlave.class::isInstance)
                .map(KubernetesSlave.class::cast)
                .filter(ks -> Objects.equals(ks.getNamespace(), namespace) && Objects.equals(ks.getPodName(), name))
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
                    listener.onEvent(action, optionalNode.get(), pod, terminationReasons.get(optionalNode.get().getNodeName()));
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
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "Confused by @org.checkerframework.checker.nullness.qual.Nullable on LoadingCache.get? Never null here.")
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
         *
         * @param action the kind of event that happened to the referred pod
         * @param node The affected node
         * @param pod The affected pod
         */
        void onEvent(@NonNull Watcher.Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReaons) throws IOException, InterruptedException;
    }

    @Extension
    public static class RemoveAgentOnPodDeleted implements Listener {
        @Override
        public void onEvent(@NonNull Watcher.Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReasons) throws IOException {
            if (action != Watcher.Action.DELETED) {
                return;
            }
            String ns = pod.getMetadata().getNamespace();
            String name = pod.getMetadata().getName();
            LOGGER.info(() -> ns + "/" + name + " was just deleted, so removing corresponding Jenkins agent");
            node.getRunListener().getLogger().printf("Pod %s/%s was just deleted%n", ns, name);
            Jenkins.get().removeNode(node);
        }
    }

    @Extension
    public static class TerminateAgentOnContainerTerminated implements Listener {

        @Override
        public void onEvent(@NonNull Watcher.Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReasons) throws IOException, InterruptedException {
            if (action != Watcher.Action.MODIFIED) {
                return;
            }
            List<ContainerStatus> terminatedContainers = PodUtils.getTerminatedContainers(pod);
            if (!terminatedContainers.isEmpty()) {
                String ns = pod.getMetadata().getNamespace();
                String name = pod.getMetadata().getName();
                TaskListener runListener = node.getRunListener();
                terminatedContainers.forEach(c -> {
                    ContainerStateTerminated t = c.getState().getTerminated();
                    LOGGER.info(() -> ns + "/" + name + " Container " + c.getName() + " was just terminated, so removing the corresponding Jenkins agent");
                    String reason = t.getReason();
                    runListener.getLogger().printf("%s/%s Container %s was terminated (Exit Code: %d, Reason: %s)%n", ns, name, c.getName(), t.getExitCode(), reason);
                    if (reason != null) {
                        terminationReasons.add(reason);
                    }
                });
                logLastLinesThenTerminateNode(node, pod, runListener);
                PodUtils.cancelQueueItemFor(pod, "ContainerError");
            }
        }
    }

    @Extension
    public static class TerminateAgentOnPodFailed implements Listener {
        @Override
        public void onEvent(@NonNull Watcher.Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReasons) throws IOException, InterruptedException {
            if (action != Watcher.Action.MODIFIED) {
                return;
            }
            if ("Failed".equals(pod.getStatus().getPhase())) {
                String ns = pod.getMetadata().getNamespace();
                String name = pod.getMetadata().getName();
                TaskListener runListener = node.getRunListener();
                String reason = pod.getStatus().getReason();
                LOGGER.info(() -> ns + "/" + name + " Pod just failed. Removing the corresponding Jenkins agent. Reason: " + reason + ", Message: " + pod.getStatus().getMessage());
                runListener.getLogger().printf("%s/%s Pod just failed (Reason: %s, Message: %s)%n", ns, name, reason, pod.getStatus().getMessage());
                if (reason != null) {
                    terminationReasons.add(reason);
                }
                logLastLinesThenTerminateNode(node, pod, runListener);
            }
        }
    }

    private static void logLastLinesThenTerminateNode(KubernetesSlave node, Pod pod, TaskListener runListener) throws IOException, InterruptedException {
        try {
            String lines = PodUtils.logLastLines(pod, node.getKubernetesCloud().connect());
            if (lines != null) {
                runListener.getLogger().print(lines);
            }
        } catch (KubernetesAuthException e) {
            LOGGER.log(Level.FINE, e, () -> "Unable to get logs after pod failed event");
        } finally {
            node.terminate();
        }
    }

    @Extension
    public static class TerminateAgentOnImagePullBackOff implements Listener {

        @Override
        public void onEvent(@NonNull Watcher.Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReasons) throws IOException, InterruptedException {
            if (action != Watcher.Action.MODIFIED) {
                return;
            }

            List<ContainerStatus> backOffContainers = PodUtils.getContainers(pod, cs -> {
                ContainerStateWaiting waiting = cs.getState().getWaiting();
                return waiting != null && waiting.getMessage() != null && waiting.getMessage().contains("Back-off pulling image");
            });
            if (backOffContainers.isEmpty()) {
                return;
            }
            backOffContainers.forEach(cs -> node.getRunListener().error("Unable to pull Docker image \"" + cs.getImage() + "\". Check if image tag name is spelled correctly."));
            terminationReasons.add("ImagePullBackOff");
            PodUtils.cancelQueueItemFor(pod, "ImagePullBackOff");
            node.terminate();
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
