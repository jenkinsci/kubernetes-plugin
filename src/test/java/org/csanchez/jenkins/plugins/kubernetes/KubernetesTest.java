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

package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import hudson.model.Label;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Default;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume;
import org.jenkinsci.plugins.kubernetes.credentials.FileSystemServiceAccountCredential;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.recipes.LocalData;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.plugins.git.GitTool;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tools.ToolLocationNodeProperty;
import hudson.tools.ToolLocationNodeProperty.ToolLocation;
import hudson.util.DescribableList;
import hudson.util.Secret;

/**
 * @author Carlos Sanchez
 * @since 0.9
 *
 */
public class KubernetesTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule log = new LoggerRule();

    private KubernetesCloud cloud;

    @Before
    public void before() throws Exception {
        cloud = r.jenkins.clouds.get(KubernetesCloud.class);
        assertNotNull(cloud);
    }

    @Test
    @LocalData()
    public void upgradeFrom_1_17_2() throws Exception {
        Map<String, String> labels = cloud.getPodLabelsMap();
        assertEquals(2, labels.size());
        assertThat(cloud.getPodLabelsMap(), hasEntry("jenkins", "slave"));
        assertThat(cloud.getPodLabelsMap(), hasEntry("biff", "johnson"));
        PodTemplate pt = cloud.getTemplate(Label.get("java"));
        assertNotNull(pt);
        for (ContainerTemplate ct : pt.getContainers()) {
            // Retain working dir used in previous version
            assertEquals("/home/jenkins", ct.getWorkingDir());
        }
    }

    @Test
    @LocalData
    public void upgradeFrom_1_15_9() {
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
        PodTemplate template = templates.get(0);
        assertEquals("blah", template.getYaml());
        assertEquals(Collections.singletonList("blah"), template.getYamls());
        assertNull(template._getYamls());
    }

    @Test
    @LocalData
    public void upgradeFrom_1_15_9_invalid() {
        log.record(PodTemplate.class, Level.WARNING).capture(1);
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
        PodTemplate template = templates.get(0);
        assertEquals("blah", template.getYaml());
        assertEquals(Collections.singletonList("blah"), template.getYamls());
        assertNull(template._getYamls());
        log.getMessages().stream().anyMatch(msg -> msg.contains("Found several persisted YAML fragments in pod template java"));
    }

    @Test
    @LocalData()
    @Issue("JENKINS-57116")
    public void upgradeFrom_1_15_1() throws Exception {
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
        PodTemplate template = templates.get(0);
        assertEquals(Collections.emptyList(), template.getYamls());
        assertNull(template.getYaml());
    }

    @Test
    @LocalData()
    public void upgradeFrom_1_10() throws Exception {
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
        assertEquals(new Never(), cloud.getPodRetention());
        PodTemplate template = templates.get(0);
        assertEquals(new Default(), template.getPodRetention());
        assertEquals(cloud.DEFAULT_WAIT_FOR_POD_SEC, cloud.getWaitForPodSec());
        assertTrue(template.isShowRawYaml());
        assertEquals(Collections.emptyList(), template.getYamls());
        assertNull(template.getYaml());
    }

    @Test
    @LocalData()
    public void upgradeFrom_1_1() throws Exception {
        List<Credentials> credentials = SystemCredentialsProvider.getInstance().getCredentials();
        assertEquals(3, credentials.size());
        UsernamePasswordCredentialsImpl cred0 = (UsernamePasswordCredentialsImpl) credentials.get(0);
        assertEquals("token", cred0.getId());
        assertEquals("myusername", cred0.getUsername());
        FileSystemServiceAccountCredential cred1 = (FileSystemServiceAccountCredential) credentials.get(1);
        StringCredentialsImpl cred2 = (StringCredentialsImpl) credentials.get(2);
        assertEquals("mytoken", Secret.toString(cred2.getSecret()));
        assertThat(cloud.getLabels(), hasEntry("jenkins", "slave"));
        assertEquals(cloud.DEFAULT_WAIT_FOR_POD_SEC, cloud.getWaitForPodSec());
    }

    @Test
    @LocalData()
    public void upgradeFrom_0_12() throws Exception {
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
        PodTemplate template = templates.get(0);
        assertEquals(Arrays.asList(new KeyValueEnvVar("pod_a_key", "pod_a_value"),
                new KeyValueEnvVar("pod_b_key", "pod_b_value")), template.getEnvVars());
        assertEquals(Collections.emptyList(), template.getYamls());
        assertEquals(cloud.DEFAULT_WAIT_FOR_POD_SEC, cloud.getWaitForPodSec());
    }

    @Test
    @LocalData()
    public void upgradeFrom_0_10() throws Exception {
        List<PodTemplate> templates = cloud.getTemplates();
        PodTemplate template = templates.get(0);
        DescribableList<NodeProperty<?>,NodePropertyDescriptor> nodeProperties = template.getNodeProperties();
        assertEquals(1, nodeProperties.size());
        ToolLocationNodeProperty property = (ToolLocationNodeProperty) nodeProperties.get(0);
        assertEquals(1, property.getLocations().size());
        ToolLocation location = property.getLocations().get(0);
        assertEquals("Default", location.getName());
        assertEquals("/custom/path", location.getHome());
        assertEquals(GitTool.class, location.getType().clazz);
        assertEquals(cloud.DEFAULT_WAIT_FOR_POD_SEC, cloud.getWaitForPodSec());
    }

    @Test
    @LocalData()
    public void upgradeFrom_0_8() throws Exception {
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
        assertEquals(cloud.DEFAULT_WAIT_FOR_POD_SEC, cloud.getWaitForPodSec());
    }

    private void assertPodTemplates(List<PodTemplate> templates) {
        assertEquals(1, templates.size());
        PodTemplate podTemplate = templates.get(0);
        assertEquals(Integer.MAX_VALUE, podTemplate.getInstanceCap());
        assertEquals(1, podTemplate.getContainers().size());
        ContainerTemplate containerTemplate = podTemplate.getContainers().get(0);
        assertEquals("jenkins/inbound-agent", containerTemplate.getImage());
        assertEquals("jnlp", containerTemplate.getName());
        assertEquals(Arrays.asList(new KeyValueEnvVar("a", "b"), new KeyValueEnvVar("c", "d")),
                containerTemplate.getEnvVars());
        assertEquals(2, podTemplate.getVolumes().size());

        EmptyDirVolume emptyVolume = (EmptyDirVolume) podTemplate.getVolumes().get(0);
        assertEquals("/mnt", emptyVolume.getMountPath());
        assertFalse(emptyVolume.getMemory());
        assertEquals(EmptyDirVolume.class.getName(), emptyVolume.getClass().getName());

        HostPathVolume hostPathVolume = (HostPathVolume) podTemplate.getVolumes().get(1);
        assertEquals("/host", hostPathVolume.getMountPath());
        assertEquals("/mnt/host", hostPathVolume.getHostPath());
        assertEquals(HostPathVolume.class.getName(), hostPathVolume.getClass().getName());

        assertEquals(0, podTemplate.getActiveDeadlineSeconds());

    }
}
