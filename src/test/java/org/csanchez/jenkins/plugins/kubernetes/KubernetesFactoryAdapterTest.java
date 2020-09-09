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

    private static final String[] SYSTEM_PROPERTY_NAMES = new String[] { //
            KUBERNETES_KUBECONFIG_FILE, //
            KUBERNETES_NAMESPACE_FILE, //
            KUBERNETES_HTTP_PROXY, //
            KUBERNETES_HTTPS_PROXY, //
            KUBERNETES_NO_PROXY, //
            KUBERNETES_PROXY_USERNAME, //
            KUBERNETES_PROXY_PASSWORD //
    };

    private static final String HTTP_PROXY = "http://example.com:123";
    private static final String HTTPS_PROXY = "https://example.com:123";
    private static final String NO_PROXY = "noproxy";
    private static final String PROXY_USERNAME = "proxy_username";
    private static final String PROXY_PASSWORD = "proxy_password";

    private Map<String, String> systemProperties = new HashMap<>();

    @Before
    public void saveSystemProperties() {
        for (String key : SYSTEM_PROPERTY_NAMES) {
            systemProperties.put(key, System.getProperty(key));
            // use bogus values to avoid influence from environment
            System.setProperty(key, "src/test/resources/void");
        }

        // proxy system properties
        System.setProperty(KUBERNETES_HTTP_PROXY, HTTP_PROXY);
        System.setProperty(KUBERNETES_HTTPS_PROXY, HTTPS_PROXY);
        System.setProperty(KUBERNETES_NO_PROXY, NO_PROXY);
        System.setProperty(KUBERNETES_PROXY_USERNAME, PROXY_USERNAME);
        System.setProperty(KUBERNETES_PROXY_PASSWORD, PROXY_PASSWORD);
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
    public void autoConfig() throws Exception {
        System.setProperty(KUBERNETES_NAMESPACE_FILE, "src/test/resources/kubenamespace");
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter(null, null, null, false);
        KubernetesClient client = factory.createClient();
        assertEquals("test-namespace", client.getNamespace());
        assertEquals(HTTP_PROXY, client.getConfiguration().getHttpProxy());
        assertEquals(HTTPS_PROXY, client.getConfiguration().getHttpsProxy());
        assertArrayEquals(new String[] { NO_PROXY }, client.getConfiguration().getNoProxy());
        assertEquals(PROXY_USERNAME, client.getConfiguration().getProxyUsername());
        assertEquals(PROXY_PASSWORD, client.getConfiguration().getProxyPassword());
    }

    @Test
    public void autoConfigWithMasterUrl() throws Exception {
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter("http://example.com", null, null, false);
        KubernetesClient client = factory.createClient();
        assertEquals(HTTP_PROXY, client.getConfiguration().getHttpProxy());
        assertEquals(HTTPS_PROXY, client.getConfiguration().getHttpsProxy());
        assertArrayEquals(new String[] { NO_PROXY }, client.getConfiguration().getNoProxy());
        assertEquals(PROXY_USERNAME, client.getConfiguration().getProxyUsername());
        assertEquals(PROXY_PASSWORD, client.getConfiguration().getProxyPassword());
    }
}
