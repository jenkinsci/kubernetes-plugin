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

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Node;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;

/**
 * helper class for steps running in a kubernetes `node` context
 */
class KubernetesNodeContext {
    static final transient String HOSTNAME_FILE = "/etc/hostname";
    private StepContext context;
    private FilePath workspace;

    public KubernetesNodeContext(StepContext context) throws Exception {
        this.context = context;
        workspace = context.get(FilePath.class);
    }

    public String getPodName() throws Exception {
        return workspace.child(HOSTNAME_FILE).readToString().trim();
    }

    public String getNamespace() throws Exception {
        return workspace.child(Config.KUBERNETES_NAMESPACE_PATH).readToString().trim();
    }

    public KubernetesClient connectToCloud() throws Exception {
        Node node = context.get(Node.class);
        if (! (node instanceof KubernetesSlave)) {
            throw new AbortException(String.format("Node is not a Kubernetes node: %s", node.getNodeName()));
        }
        KubernetesSlave slave = (KubernetesSlave) node;
        KubernetesCloud cloud = (KubernetesCloud) slave.getCloud();
        if (cloud == null) {
            throw new AbortException(String.format("Cloud does not exist: %s", slave.getCloudName()));
        }
        return cloud.connect();
    }
}
