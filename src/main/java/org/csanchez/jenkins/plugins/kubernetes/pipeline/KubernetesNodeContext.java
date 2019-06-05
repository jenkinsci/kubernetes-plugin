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

import java.io.IOException;
import java.io.Serializable;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.AbortException;
import hudson.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * helper class for steps running in a kubernetes `node` context
 */
class KubernetesNodeContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private StepContext context;

    private String podName;
    private String namespace;

    KubernetesNodeContext(StepContext context) throws Exception {
        this.context = context;
        KubernetesSlave agent = getKubernetesSlave();
        this.podName = agent.getPodName();
        this.namespace = agent.getNamespace();
    }

    // TODO remove the Exception thrown
    String getPodName() throws Exception {
        return podName;
    }

    // TODO remove the Exception thrown
    public String getNamespace() throws Exception {
        return namespace;
    }

    KubernetesClient connectToCloud() throws Exception {
        return getKubernetesSlave().getKubernetesCloud().connect();
    }

    private KubernetesSlave getKubernetesSlave() throws IOException, InterruptedException {
        Node node = context.get(Node.class);
        if (!(node instanceof KubernetesSlave)) {
            throw new AbortException(
                    String.format("Node is not a Kubernetes node: %s", node != null ? node.getNodeName() : null));
        }
        return (KubernetesSlave) node;
    }
}
