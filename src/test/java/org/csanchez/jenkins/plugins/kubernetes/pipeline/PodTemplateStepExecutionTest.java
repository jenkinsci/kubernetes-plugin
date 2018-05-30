/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;
import static org.junit.Assert.*;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.Messages;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;

import hudson.model.Result;

public class PodTemplateStepExecutionTest {

    @Rule
    public JenkinsRuleNonLocalhost r = new JenkinsRuleNonLocalhost();

    protected KubernetesCloud cloud;

    @Before
    public void configureCloud() throws Exception {
        cloud = new KubernetesCloud("kubernetes");
        r.jenkins.clouds.add(cloud);
    }

    private String loadPipelineScript(String name) {
        return AbstractKubernetesPipelineTest.loadPipelineScript(getClass(), name);
    }

    @Test
    public void testBadNameDetection() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "bad_container_name");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("badcontainername.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        r.assertLogContains(Messages.RFC1123_error("badcontainerName_!"), b);
    }

    @Test
    public void testBadNameYamlDetection() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "bad_container_name_yaml");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("badcontainernameyaml.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        r.assertLogContains(Messages.RFC1123_error("badcontainername_!, badcontainername2_!"), b);
    }

    @Test
    public void testBadLabel() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "bad_label");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("badlabel.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        r.assertLogContains(Messages.label_error("mypod!123"), b);
    }

}
