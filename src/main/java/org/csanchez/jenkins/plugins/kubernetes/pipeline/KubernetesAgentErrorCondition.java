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
import hudson.model.TaskListener;
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
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.steps.AgentErrorCondition;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

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

    private boolean handleNonKubernetes;

    @DataBoundConstructor public KubernetesAgentErrorCondition() {}

    public boolean isHandleNonKubernetes() {
        return handleNonKubernetes;
    }

    @DataBoundSetter public void setHandleNonKubernetes(boolean handleNonKubernetes) {
        this.handleNonKubernetes = handleNonKubernetes;
    }

    @Override
    public boolean test(Throwable t, StepContext context) throws IOException, InterruptedException {
        if (context == null) {
            LOGGER.fine("Cannot check error without context");
            return handleNonKubernetes;
        }
        if (!new AgentErrorCondition().test(t, context)) {
            LOGGER.fine(() -> "Not a recognized failure: " + t);
            return false;
        }
        TaskListener listener = context.get(TaskListener.class);
        FlowNode _origin = ErrorAction.findOrigin(t, context.get(FlowExecution.class));
        if (_origin == null) {
            if (!handleNonKubernetes) {
                listener.getLogger().println("Unable to identify source of error (" + t + ") to see if this was associated with a Kubernetes agent");
            }
            return handleNonKubernetes;
        }
        FlowNode origin = _origin instanceof BlockEndNode ? ((BlockEndNode) _origin).getStartNode() : _origin;
        LOGGER.fine(() -> "Found origin " + origin + " " + origin.getDisplayFunctionName());
        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
        scanner.setup(origin);
        boolean foundPodTemplate = false;
        for (FlowNode callStack : scanner) {
            WorkspaceAction ws = callStack.getPersistentAction(WorkspaceAction.class);
            if (ws != null) {
                String node = ws.getNode();
                Node n = Jenkins.get().getNode(node);
                if (n != null) {
                    if (!(n instanceof KubernetesSlave)) {
                        if (!handleNonKubernetes) {
                            listener.getLogger().println(node + " was not a Kubernetes agent");
                        }
                        return handleNonKubernetes;
                    }
                } else {
                    // May have been removed already, but we can look up the labels to see what it was.
                    Set<LabelAtom> labels = ws.getLabels();
                    if (labels.stream().noneMatch(l -> Jenkins.get().clouds.stream().anyMatch(c -> c instanceof KubernetesCloud && ((KubernetesCloud) c).getTemplate(l) != null))) {
                        if (!handleNonKubernetes) {
                            listener.getLogger().println(node + " did not look like a Kubernetes agent judging by " + labels + "; make sure retry is inside podTemplate, not outside");
                        }
                        return handleNonKubernetes;
                    }
                }
                Set<String> terminationReasons = ExtensionList.lookupSingleton(Reaper.class).terminationReasons(node);
                if (terminationReasons.stream().anyMatch(r -> IGNORED_CONTAINER_TERMINATION_REASONS.contains(r))) {
                    listener.getLogger().println("Ignored termination reason(s) for " + node + " for purposes of retry: " + terminationReasons);
                    return false;
                }
                LOGGER.fine(() -> "active on " + node + " (termination reasons: " + terminationReasons + ")");
                return true;
            }
            foundPodTemplate |= callStack instanceof StepNode && ((StepNode) callStack).getDescriptor() instanceof PodTemplateStep.DescriptorImpl;
        }
        if (!handleNonKubernetes) {
            if (foundPodTemplate) {
                listener.getLogger().println("Could not find a node block associated with " + origin.getDisplayFunctionName() + " (source of error) but inside podTemplate");
                return true;
            } else {
                listener.getLogger().println("Could not find a node block associated with " + origin.getDisplayFunctionName() + " (source of error)");
            }
        }
        return handleNonKubernetes;
    }

    @Symbol("kubernetesAgent")
    @Extension public static final class DescriptorImpl extends ErrorConditionDescriptor {

        @Override public String getDisplayName() {
            return "Kubernetes agent errors";
        }

    }

}
