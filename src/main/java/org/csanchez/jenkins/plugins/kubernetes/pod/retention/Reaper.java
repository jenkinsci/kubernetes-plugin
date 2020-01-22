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

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import hudson.slaves.ComputerListener;
import hudson.slaves.EphemeralNode;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;

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
     * Activate this feature only if and when some Kubernetes agent is actually used.
     * Avoids touching the API server when this plugin is not even in use.
     */
    private final AtomicBoolean activated = new AtomicBoolean();

    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException, InterruptedException {
        if (c instanceof KubernetesComputer && activated.compareAndSet(false, true)) {
            activate();
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
                client.pods().inNamespace(client.getNamespace()).watch(this);
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "failed to set up watcher on " + kc.getDisplayName(), x);
            }
        }
    }

    @Override
    public void eventReceived(Watcher.Action action, Pod pod) {
        if (action == Watcher.Action.DELETED) {
            String ns = pod.getMetadata().getNamespace();
            String name = pod.getMetadata().getName();
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null) {
                for (Node n : new ArrayList<>(jenkins.getNodes())) {
                    if (!(n instanceof KubernetesSlave)) {
                        continue;
                    }
                    KubernetesSlave ks = (KubernetesSlave) n;
                    if (ks.getNamespace().equals(ns) && ks.getPodName().equals(name)) {
                        LOGGER.info(() -> ns + "/" + name + " was just deleted, so removing corresponding Jenkins agent");
                        try {
                            jenkins.removeNode(ks);
                            return;
                        } catch (Exception x) {
                            LOGGER.log(Level.WARNING, "failed to reap " + ns + "/" + name, x);
                        }
                    }
                }
            }
            LOGGER.fine(() -> "received deletion notice for " + ns + "/" + name + " which does not seem to correspond to any Jenkins agent");
        }
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        // TODO ignore, or do we need to manually reattach the watcher?
        // AllContainersRunningPodWatcher is not reattached, but this is expected to be short-lived,
        // useful only until the containers of a single pod start running.
        // (At least when using kubernetes-client/java, the connection gets closed after 2m on GKE
        // and you need to rerun the watch. Does the fabric8io client wrap this?)
    }

}
