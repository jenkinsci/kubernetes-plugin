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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;

import hudson.ExtensionList;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.GroovySample;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class KubernetesSamplesTest extends AbstractKubernetesPipelineTest {

    @BeforeEach
    void beforeEach() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
    }

    @Test
    void smokes() throws Exception {
        // TODO tried without success to use Parameterized here (need to construct parameters _after_ JenkinsRule
        // starts)
        for (GroovySample gs : ExtensionList.lookup(GroovySample.class)) {
            if (gs.name().equals("kubernetes-windows") && !isWindows(null)) {
                System.err.println("==== Skipping " + gs.title() + " ====");
                continue;
            }
            System.err.println("==== " + gs.title() + " ====");
            p = r.createProject(WorkflowJob.class, gs.name());
            p.setDefinition(new CpsFlowDefinition(gs.script(), true));
            r.buildAndAssertSuccess(p);
        }
    }
}
