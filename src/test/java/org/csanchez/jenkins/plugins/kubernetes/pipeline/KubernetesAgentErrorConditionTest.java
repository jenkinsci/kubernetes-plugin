/*
 * Copyright 2022 CloudBees, Inc.
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

import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.Label;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.slaves.OfflineCause;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.InboundAgentRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

@Issue("JENKINS-49707")
public class KubernetesAgentErrorConditionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public InboundAgentRule inboundAgents = new InboundAgentRule();
    @Rule public LoggerRule logging = new LoggerRule().record(KubernetesAgentErrorCondition.class, Level.FINE);

    @Test public void handleNonKubernetes() throws Exception {
        Slave s = r.createSlave(Label.get("remote")); // *not* a KubernetesSlave
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.addProperty(new ParametersDefinitionProperty(new BooleanParameterDefinition("HNK")));
        p.setDefinition(new CpsFlowDefinition(
            "retry(count: 2, conditions: [kubernetesAgent(handleNonKubernetes: params.HNK)]) {\n" +
            "  node('remote') {\n" +
            "    semaphore 'wait'\n" +
            "    pwd()\n" +
            "  }\n" +
            "}", true));
        WorkflowRun b = p.scheduleBuild2(0, new ParametersAction(new BooleanParameterValue("HNK", false))).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b);
        s.toComputer().disconnect(new OfflineCause.UserCause(null, null));
        while (s.toComputer().isOnline()) {
            Thread.sleep(100);
        }
        SemaphoreStep.success("wait/1", null);
        s.toComputer().connect(false);
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        b = p.scheduleBuild2(0, new ParametersAction(new BooleanParameterValue("HNK", true))).waitForStart();
        SemaphoreStep.waitForStart("wait/2", b);
        s.toComputer().disconnect(new OfflineCause.UserCause(null, null));
        while (s.toComputer().isOnline()) {
            Thread.sleep(100);
        }
        SemaphoreStep.success("wait/2", null);
        SemaphoreStep.success("wait/3", null);
        s.toComputer().connect(false);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
    }

}
