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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.affinities.Affinity;
import org.csanchez.jenkins.plugins.kubernetes.affinities.NodeAffinity;
import org.csanchez.jenkins.plugins.kubernetes.affinities.PodAffinity;
import org.csanchez.jenkins.plugins.kubernetes.affinities.PodAntiAffinity;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author Carlos Sanchez
 */
public class KubernetesAffinityTest extends AbstractKubernetesPipelineTest {

    private static final Logger LOGGER = Logger.getLogger(KubernetesAffinityTest.class.getName());

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    //Node & Pod affinities to follow

    @Test
    public void runInPodWithNodeAffinity() throws Exception {
        deletePods(cloud.connect(), Collections.emptyMap(), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pod with node affinity");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithNodeAffinity.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        Thread.sleep(1000);
        List<PodTemplate> templates = cloud.getTemplates();

        List<Affinity> nodeAffinities = new ArrayList<>();

        assertFalse(templates.isEmpty());

        for(PodTemplate podTemplate: templates) {
            if(podTemplate.getLabel().equals("node-affinities")) {
                nodeAffinities = podTemplate.getAffinities();
                break;
            }
        }

        assertFalse(nodeAffinities.isEmpty());

        //Affinity should be a node affinity
        Affinity affinity = nodeAffinities.get(0);
        assertTrue(affinity instanceof NodeAffinity);
        io.fabric8.kubernetes.api.model.NodeAffinity nodeAffinity = ((NodeAffinity) affinity).buildAffinity();

        assertFalse(nodeAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().isEmpty());
        assertFalse(nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms().isEmpty());

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), KubernetesCloud.DEFAULT_POD_LABELS, true));

    }

    @Test
    public void runInPodWithPodAffinity() throws Exception {
        deletePods(cloud.connect(), Collections.emptyMap(), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pod with pod affinity");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithPodAffinity.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        Thread.sleep(1000);

        List<PodTemplate> templates = cloud.getTemplates();

        List<Affinity> podAffinities = new ArrayList<>();

        assertFalse(templates.isEmpty());

        for(PodTemplate podTemplate: templates) {
            if(podTemplate.getLabel().equals("pod-affinity")) {
                podAffinities = podTemplate.getAffinities();
                break;
            }
        }


        //There should be an affinity
        assertFalse(podAffinities.isEmpty());

        //Affinity should be a pod affinity
        Affinity affinity = podAffinities.get(0);
        assertTrue(affinity instanceof PodAffinity);
        io.fabric8.kubernetes.api.model.PodAffinity podAffinity = ((PodAffinity) affinity).buildAffinity();

        assertFalse(podAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().isEmpty());
        assertFalse(podAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().isEmpty());

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), KubernetesCloud.DEFAULT_POD_LABELS, true));

    }

    @Test
    public void runInPodWithAntiAffinity() throws Exception {
        deletePods(cloud.connect(), Collections.emptyMap(), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pod with pod anti affinity");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithPodAntiAffinity.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        Thread.sleep(1000);

        List<PodTemplate> templates = cloud.getTemplates();

        List<Affinity> podAffinities = new ArrayList<>();

        assertFalse(templates.isEmpty());

        for(PodTemplate podTemplate: templates) {
            if(podTemplate.getLabel().equals("pod-anti-affinity")) {
                podAffinities = podTemplate.getAffinities();
                break;
            }
        }

        //There should be an affinity
        assertFalse(podAffinities.isEmpty());

        //Affinity should be a pod affinity
        Affinity affinity = podAffinities.get(0);
        assertTrue(affinity instanceof PodAntiAffinity);
        io.fabric8.kubernetes.api.model.PodAntiAffinity podAntiAffinity = ((PodAntiAffinity) affinity).buildAffinity();

        assertFalse(podAntiAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().isEmpty());
        assertFalse(podAntiAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().isEmpty());

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), KubernetesCloud.DEFAULT_POD_LABELS, true));

    }

    @Test
    public void runInPodWithMixedPodAffinities() throws Exception {
        deletePods(cloud.connect(), Collections.emptyMap(), false);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "pod with mixed pod affinities");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithMixedAffinities.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);

        Thread.sleep(1000);

        List<PodTemplate> templates = cloud.getTemplates();

        List<Affinity> podAffinities = new ArrayList<>();

        assertFalse(templates.isEmpty());

        for(PodTemplate podTemplate: templates) {
            if(podTemplate.getLabel().equals("mixed-affinities")) {
                podAffinities = podTemplate.getAffinities();
                break;
            }
        }

        //There should be an affinity
        assertFalse(podAffinities.isEmpty());

        //First affinity should be a pod affinity
        Affinity affinity = podAffinities.get(0);
        assertTrue(affinity instanceof PodAffinity);
        io.fabric8.kubernetes.api.model.PodAffinity podAffinity = ((PodAffinity) affinity).buildAffinity();

        assertTrue(podAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().isEmpty());
        assertFalse(podAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().isEmpty());

        //Second affinity should be a pod antiaffinity
        Affinity antiAffinity = podAffinities.get(1);
        assertTrue(antiAffinity instanceof PodAntiAffinity);
        io.fabric8.kubernetes.api.model.PodAntiAffinity podAntiAffinity = ((PodAntiAffinity) antiAffinity).buildAffinity();

        assertTrue(podAntiAffinity.getPreferredDuringSchedulingIgnoredDuringExecution().isEmpty());
        assertFalse(podAntiAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().isEmpty());

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Apache Maven 3.3.9", b);
        assertFalse("There are pods leftover after test execution, see previous logs",
                deletePods(cloud.connect(), KubernetesCloud.DEFAULT_POD_LABELS, true));


    }

}
