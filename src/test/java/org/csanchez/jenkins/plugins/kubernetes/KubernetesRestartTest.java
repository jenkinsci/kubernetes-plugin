/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees
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

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;

public class KubernetesRestartTest {

    @Rule
    public JenkinsSessionRule s = new JenkinsSessionRule();

    @Test
    @LocalData
    public void upgradeFrom_1_27_1() throws Throwable {
        AtomicReference<String> podTemplateId = new AtomicReference<>();
        AtomicReference<String> toString = new AtomicReference<>();
        s.then(r -> {
            KubernetesCloud cloud = r.jenkins.clouds.get(KubernetesCloud.class);
            List<PodTemplate> templates = cloud.getTemplates();
            assertThat(templates, hasSize(1));
            PodTemplate podTemplate = templates.get(0);
            podTemplateId.set(podTemplate.getId());
            toString.set(podTemplate.toString());
        });
        s.then(r -> {
            KubernetesCloud cloud = r.jenkins.clouds.get(KubernetesCloud.class);
            List<PodTemplate> templates = cloud.getTemplates();
            assertThat(templates, hasSize(1));
            PodTemplate podTemplate = templates.get(0);
            assertEquals(toString.get(), podTemplate.toString());
            assertEquals(podTemplateId.get(), podTemplate.getId());
        });
    }
}
