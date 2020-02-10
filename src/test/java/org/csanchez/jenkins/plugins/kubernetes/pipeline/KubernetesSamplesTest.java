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

import hudson.ExtensionList;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.GroovySample;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

public class KubernetesSamplesTest extends AbstractKubernetesPipelineTest {

    // TODO tried without success to use Parameterized here (need to construct parameters _after_ JenkinsRule starts)
    @Rule public ErrorCollector errors = new ErrorCollector();

    {
        r.timeout *= 2; // again, without Parameterized we are running a bunch of builds in one test case
    }

    @Before public void setUp() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
    }

    @Test public void smokes() throws Exception {
        for (GroovySample gs : ExtensionList.lookup(GroovySample.class)) {
            System.err.println("==== " + gs.title() + " ====");
            p = r.createProject(WorkflowJob.class, gs.name());
            p.setDefinition(new CpsFlowDefinition(gs.script(), true));
            errors.checkSucceeds(() -> r.buildAndAssertSuccess(p));
        }
    }

}
