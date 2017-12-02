/*
 * The MIT License
 *
 * Copyright (c) 2017, Carlos Sanchez
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

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.fabric8.kubernetes.client.Config.*;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Test the creation of clients
 */
public class KubernetesFactoryAdapterTest {

    private static final String[] SYSTEM_PROPERTY_NAMES = new String[] { KUBERNETES_KUBECONFIG_FILE,
            KUBERNETES_NAMESPACE_FILE };

    private Map<String, String> systemProperties = new HashMap<>();

    @Before
    public void saveSystemProperties() {
        for (String key : SYSTEM_PROPERTY_NAMES) {
            systemProperties.put(key, System.getProperty(key));
            // use bogus values to avoid influence from environment
            System.setProperty(key, "src/test/resources/void");
        }
    }

    @After
    public void restoreSystemProperties() {
        for (String key : systemProperties.keySet()) {
            String s = systemProperties.get(key);
            if (s != null) {
                System.setProperty(key, systemProperties.get(key));
            } else {
                System.clearProperty(key);
            }
        }
    }

    @Test
    public void defaultNamespace() throws Exception {
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter(null, null, null, false);
        KubernetesClient client = factory.createClient();
        assertEquals("default", client.getNamespace());
    }

    @Test
    public void autoConfigNamespace() throws Exception {
        System.setProperty(KUBERNETES_NAMESPACE_FILE, "src/test/resources/kubenamespace");
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter(null, null, null, false);
        KubernetesClient client = factory.createClient();
        assertEquals("test-namespace", client.getNamespace());
    }
}
