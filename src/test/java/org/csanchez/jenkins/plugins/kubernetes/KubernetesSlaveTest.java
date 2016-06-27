/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import java.util.Collections;
import java.util.List;

import org.csanchez.jenkins.plugins.kubernetes.PodVolumes.PodVolume;
import org.junit.Test;

/**
 * @author Carlos Sanchez
 * @since
 *
 */
public class KubernetesSlaveTest {

    @Test
    public void testGetSlaveName() {
        List<? extends PodVolume> volumes = Collections.emptyList();
        PodTemplate template = new PodTemplate("image", volumes);
        assertNotNull(KubernetesSlave.getSlaveName(template));
        assertTrue(KubernetesSlave.getSlaveName(template).matches("^[0-9a-f]{12}$"));
        assertTrue(KubernetesSlave.getSlaveName(new PodTemplate("", "image", volumes)).matches("^[0-9a-f]{12}$"));
        assertTrue(KubernetesSlave.getSlaveName(new PodTemplate("a name", "image", volumes))
                .matches("^a-name-[0-9a-f]{12}$"));
    }

}
