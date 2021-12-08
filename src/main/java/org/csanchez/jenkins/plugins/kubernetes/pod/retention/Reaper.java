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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.Cloud;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.kubernetes.client.WatcherException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
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
public class Reaper extends ComputerListener implements Watcher<Pod> {
    
    private static final Logger LOGGER = Logger.getLogger(Reaper.class.getName());

    /**
     * Only useful for tests which shutdown Jenkins without terminating the JVM.
     * Close the watch so that we don't end up with spam in logs
     */
    @Extension
    public static class ReaperShutdownListener extends ItemListener {
        @Override
        public void onBeforeShutdown() {
            Reaper.getInstance().closeWatch();
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

    private Watch watch;

    private final Map<String, Set<String>> terminationReasons = new HashMap<>();

    @Override
    public void preLaunch(Computer c, TaskListener taskListener) throws IOException, InterruptedException {
        if (c instanceof KubernetesComputer && activated.compareAndSet(false, true)) {
            Timer.get().schedule(this::activate, 10, TimeUnit.SECONDS);
        }
    }

    private void activate() {
        LOGGER.fine("Activating reaper");
        // First check all existing nodes to see if they still have active pods.
        // (We may have missed deletion events while Jenkins was shut off,
        // or pods may have been deleted before any Kubernetes agent was brought online.)
        for (Node n : new ArrayList<>(Jenkins.get().getNodes())) {
            if (!(n instanceof KubernetesSlave)) {
                continue;
            }
            KubernetesSlave ks = (KubernetesSlave) n;
            String ns = ks.getNamespace();
            String name = ks.getPodName();
            try {
                // TODO more efficient to do a single (or paged) list request, but tricky since there may be multiple clouds,
                // and even within a single cloud an agent pod is permitted to use a nondefault namespace,
                // yet we do not want to do an unnamespaced pod list for RBAC reasons.
                // Could use a hybrid approach: first list all pods in the configured namespace for all clouds;
                // then go back and individually check any unmatched agents with their configured namespace.
                if (ks.getKubernetesCloud().connect().pods().inNamespace(ns).withName(name).get() == null) {
                    LOGGER.info(() -> ns + "/" + name + " seems to have been deleted, so removing corresponding Jenkins agent");
                    Jenkins.get().removeNode(ks);
                } else {
                    LOGGER.fine(() -> ns + "/" + name + " still seems to exist, OK");
                }
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "failed to do initial reap check for " + ns + "/" + name, x);
            }
        }
        // Now set up a watch for any subsequent pod deletions.
        for (Cloud c : Jenkins.get().clouds) {
            if (!(c instanceof KubernetesCloud)) {
                continue;
            }
            KubernetesCloud kc = (KubernetesCloud) c;
            try {
                KubernetesClient client = kc.connect();
                watch = client.pods().inNamespace(client.getNamespace()).watch(this);
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "failed to set up watcher on " + kc.getDisplayName(), x);
            }
        }
    }

    @Override
    public void eventReceived(Watcher.Action action, Pod pod) {
        String ns = pod.getMetadata().getNamespace();
        String name = pod.getMetadata().getName();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return;
        }
        Optional<KubernetesSlave> optionalNode = resolveNode(jenkins, ns, name);
        if (!optionalNode.isPresent()) {
            return;
        }
        ExtensionList.lookup(Listener.class).forEach(listener -> {
            try {
                listener.onEvent(action, optionalNode.get(), pod, terminationReasons.computeIfAbsent(optionalNode.get().getNodeName(), k -> new HashSet<>()));
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "Listener " + listener + " failed for " + ns + "/" + name, x);
            }
        });
    }

    private static Optional<KubernetesSlave> resolveNode(@NonNull Jenkins jenkins, String namespace, String name) {
        return new ArrayList<>(jenkins.getNodes()).stream()
                .filter(KubernetesSlave.class::isInstance)
                .map(KubernetesSlave.class::cast)
                .filter(ks -> Objects.equals(ks.getNamespace(), namespace) && Objects.equals(ks.getPodName(), name))
                .findFirst();
    }

    @Override
    public void onClose(WatcherException cause) {
        // TODO ignore, or do we need to manually reattach the watcher?
        // AllContainersRunningPodWatcher is not reattached, but this is expected to be short-lived,
        // useful only until the containers of a single pod start running.
        // (At least when using kubernetes-client/java, the connection gets closed after 2m on GKE
        // and you need to rerun the watch. Does the fabric8io client wrap this?)
    }

    private void closeWatch() {
        if (watch != null) {
            watch.close();
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
            Set<String> reasons = terminationReasons.get(node);
            return reasons == null ? Collections.emptySet() : new HashSet<>(reasons);
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
            if (action != Action.DELETED) {
                return;
            }
            String ns = pod.getMetadata().getNamespace();
            String name = pod.getMetadata().getName();
            TaskListener runListener = node.getTemplate().getListener();
            LOGGER.info(() -> ns + "/" + name + " was just deleted, so removing corresponding Jenkins agent");
            runListener.getLogger().printf("Pod %s/%s was just deleted%n", ns, name);
            Jenkins.get().removeNode(node);
        }
    }

    @Extension
    public static class TerminateAgentOnContainerTerminated implements Listener {

        @Override
        public void onEvent(@NonNull Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReasons) throws IOException, InterruptedException {
            if (action != Action.MODIFIED) {
                return;
            }
            List<ContainerStatus> terminatedContainers = PodUtils.getTerminatedContainers(pod);
            if (!terminatedContainers.isEmpty()) {
                String ns = pod.getMetadata().getNamespace();
                String name = pod.getMetadata().getName();
                TaskListener runListener = node.getTemplate().getListener();
                terminatedContainers.forEach(c -> {
                    ContainerStateTerminated t = c.getState().getTerminated();
                    LOGGER.info(() -> ns + "/" + name + " Container " + c.getName() + " was just terminated, so removing the corresponding Jenkins agent");
                    runListener.getLogger().printf("%s/%s Container %s was terminated (Exit Code: %d, Reason: %s)%n", ns, name, c.getName(), t.getExitCode(), t.getReason());
                    terminationReasons.add(t.getReason());
                });
                node.terminate();
            }
        }
    }

    @Extension
    public static class TerminateAgentOnPodFailed implements Listener {
        @Override
        public void onEvent(@NonNull Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReasons) throws IOException, InterruptedException {
            if (action != Action.MODIFIED) {
                return;
            }
            if ("Failed".equals(pod.getStatus().getPhase())) {
                String ns = pod.getMetadata().getNamespace();
                String name = pod.getMetadata().getName();
                TaskListener runListener = node.getTemplate().getListener();
                LOGGER.info(() -> ns + "/" + name + " Pod just failed. Removing the corresponding Jenkins agent. Reason: " + pod.getStatus().getReason() + ", Message: " + pod.getStatus().getMessage());
                runListener.getLogger().printf("%s/%s Pod just failed (Reason: %s, Message: %s)%n", ns, name, pod.getStatus().getReason(), pod.getStatus().getMessage());
                terminationReasons.add(pod.getStatus().getReason());
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
        }
    }

    @Extension
    public static class TerminateAgentOnImagePullBackOff implements Listener {

        @Override
        public void onEvent(@NonNull Action action, @NonNull KubernetesSlave node, @NonNull Pod pod, @NonNull Set<String> terminationReasons) throws IOException, InterruptedException {
            List<ContainerStatus> backOffContainers = PodUtils.getContainers(pod, cs -> {
                ContainerStateWaiting waiting = cs.getState().getWaiting();
                return waiting != null && waiting.getMessage() != null && waiting.getMessage().contains("Back-off pulling image");
            });
            if (backOffContainers.isEmpty()) {
                return;
            }
            backOffContainers.forEach(cs -> {
                TaskListener runListener = node.getTemplate().getListener();
                runListener.error("Unable to pull Docker image \""+cs.getImage()+"\". Check if image tag name is spelled correctly.");
            });
            terminationReasons.add("ImagePullBackOff");
            try (ACLContext _ = ACL.as(ACL.SYSTEM)) {
                PodUtils.cancelQueueItemFor(pod, "ImagePullBackOff");
            }
            node.terminate();
        }
    }
}
