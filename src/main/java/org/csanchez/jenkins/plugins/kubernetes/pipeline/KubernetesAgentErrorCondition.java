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
import hudson.ExtensionList;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Reaper;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.flow.ErrorCondition;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.steps.AgentErrorCondition;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Qualifies {@code node} blocks associated with {@link KubernetesSlave} to be retried if the node was deleted.
 * A more specific version of {@link AgentErrorCondition}.
 */
public class KubernetesAgentErrorCondition extends ErrorCondition {

    private static final Logger LOGGER = Logger.getLogger(KubernetesAgentErrorCondition.class.getName());

    private static final Set<String> IGNORED_CONTAINER_TERMINATION_REASONS = new HashSet<>();
    static {
        IGNORED_CONTAINER_TERMINATION_REASONS.add("OOMKilled");
        IGNORED_CONTAINER_TERMINATION_REASONS.add("Completed");
        IGNORED_CONTAINER_TERMINATION_REASONS.add("DeadlineExceeded");
    }

    @DataBoundConstructor public KubernetesAgentErrorCondition() {}

    @Override
    public boolean test(Throwable t, StepContext context) throws IOException, InterruptedException {
        if (context == null) {
            LOGGER.fine("Cannot check error without context");
            return false;
        }
        if (!new AgentErrorCondition().test(t, context)) {
            if (t instanceof FlowInterruptedException && ((FlowInterruptedException) t).getCauses().stream().anyMatch(ExecutorStepExecution.QueueTaskCancelled.class::isInstance)) {
                LOGGER.fine(() -> "QueueTaskCancelled normally ignored by AgentErrorCondition but might be delivered here from Reaper.TerminateAgentOnContainerTerminated");
                // TODO cleaner to somehow suppress that QueueTaskCancelled and let the underlying RemovedNodeCause be delivered
                // (or just let AgentErrorCondition trigger on QueueTaskCancelled)
            } else {
                LOGGER.fine(() -> "Not a recognized failure: " + t);
                return false;
            }
        }
        FlowNode _origin = ErrorAction.findOrigin(t, context.get(FlowExecution.class));
        if (_origin == null) {
            LOGGER.fine(() -> "No recognized origin of error: " + t);
            return false;
        }
        FlowNode origin = _origin instanceof BlockEndNode ? ((BlockEndNode) _origin).getStartNode() : _origin;
        LOGGER.fine(() -> "Found origin " + origin + " " + origin.getDisplayFunctionName());
        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
        scanner.setup(origin);
        for (FlowNode callStack : scanner) {
            WorkspaceAction ws = callStack.getPersistentAction(WorkspaceAction.class);
            if (ws != null) {
                String node = ws.getNode();
                Node n = Jenkins.get().getNode(node);
                if (n != null) {
                    if (!(n instanceof KubernetesSlave)) {
                        LOGGER.fine(() -> node + " was not a K8s agent");
                        return false;
                    }
                } else {
                    // May have been removed already, but we can look up the labels to see what it was.
                    Set<LabelAtom> labels = ws.getLabels();
                    if (labels.stream().noneMatch(l -> Jenkins.get().clouds.stream().anyMatch(c -> c instanceof KubernetesCloud && ((KubernetesCloud) c).getTemplate(l) != null))) {
                        LOGGER.fine(() -> node + " was not a K8s agent judging by " + labels);
                        return false;
                    }
                }
                Set<String> terminationReasons = ExtensionList.lookupSingleton(Reaper.class).terminationReasons(node);
                if (terminationReasons.stream().anyMatch(r -> IGNORED_CONTAINER_TERMINATION_REASONS.contains(r))) {
                    LOGGER.fine(() -> "ignored termination reason(s) for " + node + ": " + terminationReasons);
                    return false;
                }
                LOGGER.fine(() -> "active on " + node + " (termination reasons: " + terminationReasons + ")");
                return true;
            }
        }
        LOGGER.fine(() -> "found no WorkspaceAction starting from " + origin);
        return false;
    }

    @Symbol("kubernetesAgent")
    @Extension public static final class DescriptorImpl extends ErrorConditionDescriptor {

        @Override public String getDisplayName() {
            return "Kubernetes agent errors";
        }

    }

}
