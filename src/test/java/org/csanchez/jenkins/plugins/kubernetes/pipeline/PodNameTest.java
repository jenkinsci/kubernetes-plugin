/*
 * Copyright 2020 CloudBees, Inc.
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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Test;

public class PodNameTest extends AbstractKubernetesPipelineTest {

    @Before
    public void setUp() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
    }

    @Test
    public void trailingDots() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "whatever...");
        p.setDefinition(new CpsFlowDefinition("podTemplate {node(POD_LABEL) {sh 'echo ok'}}", true));
        r.buildAndAssertSuccess(p);
    }

}
