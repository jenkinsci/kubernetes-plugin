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

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertNotNull;

public class ContainerLogStepTest extends AbstractKubernetesPipelineTest {
    @Issue("JENKINS-46085")
    @Test
    public void simple() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "containerLog");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("getContainerLog.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("INFO: Handshaking", b);
        r.assertLogContains("INFO: Connected", b);
    }
}
