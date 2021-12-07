package org.csanchez.jenkins.plugins.kubernetes.casc;

import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.csanchez.jenkins.plugins.kubernetes.ContainerLivenessProbe;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Overrides;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CasCTest extends RoundTripAbstractTest {

    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule r, String configContent) {
        List<KubernetesCloud> all = r.j.jenkins.clouds.getAll(KubernetesCloud.class);
        assertThat(all, hasSize(1));
        KubernetesCloud cloud = all.get(0);
        assertNotNull(cloud);
        assertEquals(10,cloud.getContainerCap());
        assertEquals("http://jenkinshost:8080/jenkins/", cloud.getJenkinsUrl());
        assertEquals(32, cloud.getMaxRequestsPerHost());
        assertEquals("kubernetes", cloud.name);
        List<PodTemplate> templates = cloud.getTemplates();
        assertNotNull(templates);
        assertEquals(3, templates.size());
        PodTemplate podTemplate = templates.get(0);
        assertFalse(podTemplate.isHostNetwork());
        assertEquals("java", podTemplate.getLabel());
        assertEquals("default-java", podTemplate.getName());
        assertEquals(10, podTemplate.getInstanceCap());
        assertEquals(123, podTemplate.getSlaveConnectTimeout());
        assertEquals(5, podTemplate.getIdleMinutes());
        assertEquals(66, podTemplate.getActiveDeadlineSeconds());
        assertThat(podTemplate.getYamlMergeStrategy(), isA(Overrides.class));
        podTemplate = templates.get(1);
        assertFalse(podTemplate.isHostNetwork());
        assertEquals("dynamic-pvc", podTemplate.getLabel());
        assertEquals("dynamic-pvc", podTemplate.getName());
        assertThat(podTemplate.getYamlMergeStrategy(), isA(Overrides.class));
        WorkspaceVolume workspaceVolume = podTemplate.getWorkspaceVolume();
        assertNotNull(workspaceVolume);
        assertThat(workspaceVolume, isA(DynamicPVCWorkspaceVolume.class));
        DynamicPVCWorkspaceVolume dynamicPVCVolume = (DynamicPVCWorkspaceVolume) workspaceVolume;
        assertEquals("ReadWriteOnce", dynamicPVCVolume.getAccessModes());
        assertEquals("1",dynamicPVCVolume.getRequestsSize());
        assertEquals("hostpath",dynamicPVCVolume.getStorageClassName());
        podTemplate = templates.get(2);
        assertFalse(podTemplate.isHostNetwork());
        assertEquals("test", podTemplate.getLabel());
        assertEquals("test", podTemplate.getName());
        assertThat(podTemplate.getYamlMergeStrategy(), isA(Merge.class));
        List<ContainerTemplate> containers = podTemplate.getContainers();
        assertNotNull(containers);
        assertEquals(1, containers.size());
        ContainerTemplate container = containers.get(0);
        assertEquals("cat", container.getArgs());
        assertEquals("/bin/sh -c", container.getCommand());
        assertEquals("maven:3.6.3-jdk-8", container.getImage());
        ContainerLivenessProbe livenessProbe = container.getLivenessProbe();
        assertEquals(1, livenessProbe.getFailureThreshold());
        assertEquals(2, livenessProbe.getInitialDelaySeconds());
        assertEquals(3, livenessProbe.getPeriodSeconds());
        assertEquals(4, livenessProbe.getSuccessThreshold());
        assertEquals(5, livenessProbe.getTimeoutSeconds());
        assertEquals("maven",container.getName());
        assertTrue(container.isTtyEnabled());
        assertEquals("/src", container.getWorkingDir());

    }

    @Override
    protected String stringInLogExpected() {
        return "KubernetesCloud";
    }
}
