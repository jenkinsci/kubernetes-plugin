/*
 * Copyright (C) 2017 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.AbortException;
import hudson.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.io.IOException;
import java.io.Serializable;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * helper class for steps running in a kubernetes `node` context
 */
public class KubernetesNodeContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private final StepContext context;
    private final String podName;

    @CheckForNull
    private final String namespace;

    /**
     * Create new Kubernetes context
     * @param context step context, not null
     * @throws Exception if {@link Node} context not instance of {@link KubernetesSlave} or interrupted.
     */
    public KubernetesNodeContext(@NonNull StepContext context) throws Exception {
        this.context = context;
        KubernetesSlave agent = getKubernetesSlave();
        this.podName = agent.getPodName();
        this.namespace = agent.getNamespace();
    }

    /**
     * Kubernetes Pod name.
     * @return pod name, never {@code null}
     */
    @NonNull
    public String getPodName() {
        return podName;
    }

    /**
     * Kubernetes namespace Pod is running in.
     * @return kubernetes namespace or {@code null}
     */
    @Nullable
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get node {@link PodResource}.
     * @return client pod resource, never {@code null}
     * @throws IOException if IO exception
     * @throws InterruptedException if interrupted
     * @throws KubernetesAuthException if authentication failure
     * @see org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud#getPodResource(String, String)
     */
    @NonNull
    public PodResource getPodResource() throws IOException, InterruptedException, KubernetesAuthException {
        return getKubernetesSlave().getKubernetesCloud().getPodResource(namespace, podName);
    }

    KubernetesClient connectToCloud() throws Exception {
        return getKubernetesSlave().getKubernetesCloud().connect();
    }

    /**
     * Get {@link Node} from the {@link StepContext}. If the context instance is instance of
     * {@link KubernetesSlave} it will be returned otherwise an exception is thrown.
     * @return kubernetes slave node context, never {@code null}
     * @throws IOException if IO exception
     * @throws InterruptedException if interrupted
     * @throws AbortException if {@link Node} context is not instance of {@link KubernetesSlave}
     */
    @NonNull
    public final KubernetesSlave getKubernetesSlave() throws IOException, InterruptedException {
        Node node = context.get(Node.class);
        if (!(node instanceof KubernetesSlave)) {
            throw new AbortException(
                    String.format("Node is not a Kubernetes node: %s", node != null ? node.getNodeName() : null));
        }
        return (KubernetesSlave) node;
    }
}
