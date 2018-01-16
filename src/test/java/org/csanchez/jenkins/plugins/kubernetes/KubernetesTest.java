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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

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
        cloud = r.jenkins.clouds.get(KubernetesCloud.class);
        r.configRoundtrip();
    }

    @Test
    @LocalData()
    public void upgradeFrom_1_1() throws Exception {
        List<Credentials> credentials = SystemCredentialsProvider.getInstance().getCredentials();
        assertFalse(credentials.isEmpty());
        UsernamePasswordCredentialsImpl cred = (UsernamePasswordCredentialsImpl) credentials.get(0);
        assertEquals("token", cred.getId());
        assertEquals("myusername", cred.getUsername());
    }

    @Test
    @LocalData()
    public void upgradeFrom_0_12() throws Exception {
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
        assertEquals(Arrays.asList(new KeyValueEnvVar("pod_a_key", "pod_a_value"),
                new KeyValueEnvVar("pod_b_key", "pod_b_value")), templates.get(0).getEnvVars());
    }

    @Test
    @LocalData()
    public void upgradeFrom_0_8() throws Exception {
        List<PodTemplate> templates = cloud.getTemplates();
        assertPodTemplates(templates);
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
