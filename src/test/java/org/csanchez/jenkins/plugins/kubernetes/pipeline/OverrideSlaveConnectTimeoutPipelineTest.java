/*
 * The MIT License
 *
 * Copyright (c) 2016, Carlos Sanchez
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

import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Victor Martinez
 */
public class OverrideSlaveConnectTimeoutPipelineTest extends AbstractKubernetesPipelineTest {

    private static final Logger LOGGER = Logger.getLogger(OverrideSlaveConnectTimeoutPipelineTest.class.getName());

    @Rule
    public TestName name = new TestName();

    @Override
    protected TestName getTestName() {
        return name;
    }

    @Before
    public void setUp() {
        System.setProperty(PodTemplate.class.getName()+".connectionTimeout", "10");
    }

    @Test
    public void runInPodValidateOverrideSlaveConnectTimeout() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "Deadline");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodValidateOverrideSlaveConnectTimeout.groovy")
                , true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        r.waitForMessage("podTemplate", b);

        PodTemplate timeoutTemplate = cloud.getAllTemplates().stream().filter(x -> x.getLabel() == "runInPodValidateOverrideSlaveConnectTimeout").findAny().orElse(null);

        assertNotNull(timeoutTemplate);
        assertEquals(10, timeoutTemplate.getSlaveConnectTimeout());
        r.assertLogNotContains("Hello from container!", b);
    }
}
