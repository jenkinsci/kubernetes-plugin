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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

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
import org.jvnet.hudson.test.JenkinsRule;
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

    private KubernetesCloud cloud;

    @Before
    public void before() throws Exception {
        r.configRoundtrip();
        cloud = r.jenkins.clouds.get(KubernetesCloud.class);
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
        assertEquals(Arrays.asList(new KeyValueEnvVar("pod_a_key", "pod_a_value"),
                new KeyValueEnvVar("pod_b_key", "pod_b_value")), templates.get(0).getEnvVars());
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
        assertEquals("jenkins/jnlp-slave", containerTemplate.getImage());
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
