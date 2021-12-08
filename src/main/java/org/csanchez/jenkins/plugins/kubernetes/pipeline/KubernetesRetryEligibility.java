/*
 * Copyright 2021 CloudBees, Inc.
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

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepRetryEligibility;

/**
 * Qualifies {@code node} blocks associated with {@link KubernetesSlave} to be retried if the node was deleted.
 */
@Extension
public class KubernetesRetryEligibility implements ExecutorStepRetryEligibility {

    @Override
    public boolean shouldRetry(Throwable t, String node, String label, TaskListener listener) {
        if (ExecutorStepRetryEligibility.isRemovedNode(t) && isKubernetesAgent(node, label)) {
            listener.getLogger().println("Will retry failed node block from deleted pod " + node);
            return true;
        }
        return false;
    }

    private static boolean isKubernetesAgent(String node, String label) {
        Node current = Jenkins.get().getNode(node);
        if (current instanceof KubernetesSlave) {
            return true;
        } else if (current == null) {
            Label l = Label.get(label);
            for (Cloud c : Jenkins.get().clouds) {
                if (c instanceof KubernetesCloud && ((KubernetesCloud) c).getTemplate(l) != null) {
                    return true;
                }
            }
        }
        return false;
    }

}
