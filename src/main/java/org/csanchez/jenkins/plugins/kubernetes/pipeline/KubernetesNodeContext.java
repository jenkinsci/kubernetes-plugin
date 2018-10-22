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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.AbortException;
import hudson.model.Node;

/**
 * helper class for steps running in a kubernetes `node` context
 */
class KubernetesNodeContext {
    private StepContext context;

    KubernetesNodeContext(StepContext context) throws Exception {
        this.context = context;
    }

    String getPodName() throws Exception {
        return PodTemplateUtils.substituteEnv(getKubernetesSlave().getNodeName());
    }

    public String getNamespace() throws Exception {
        String namespace = getKubernetesSlave().getNamespace();
        return namespace != null ? namespace : connectToCloud().getNamespace();
    }

    KubernetesClient connectToCloud() throws Exception {
        return getKubernetesSlave().getKubernetesCloud().connect();
    }

    private KubernetesSlave getKubernetesSlave() throws java.io.IOException, InterruptedException {
        Node node = context.get(Node.class);
        if (! (node instanceof KubernetesSlave)) {
            throw new AbortException(String.format("Node is not a Kubernetes node: %s", node != null ? node.getNodeName() : null));
        }
        return (KubernetesSlave) node;
    }
}
