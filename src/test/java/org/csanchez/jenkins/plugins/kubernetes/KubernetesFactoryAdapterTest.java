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

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.ProxyConfiguration;
import hudson.util.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.HttpClientUtils;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_HTTPS_PROXY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_HTTP_PROXY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_KUBECONFIG_FILE;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_NAMESPACE_FILE;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_NO_PROXY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_PROXY_PASSWORD;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_PROXY_USERNAME;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_HOST_PROPERTY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_SERVICE_PORT_PROPERTY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    private static final String KUBERNETES_HOST = "kubernetes.example";
    private static final String KUBERNETES_PORT = "443";
    private static final String KUBERNETES_MASTER_URL = "https://" + KUBERNETES_HOST + ":" + KUBERNETES_PORT + "/";

    private Map<String, String> systemProperties = new HashMap<>();

    @Rule
    public JenkinsRule j = new JenkinsRule();

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
        System.setProperty(KUBERNETES_SERVICE_HOST_PROPERTY, KUBERNETES_HOST);
        System.setProperty(KUBERNETES_SERVICE_PORT_PROPERTY, KUBERNETES_PORT);
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
        assertEquals(KUBERNETES_MASTER_URL, client.getConfiguration().getMasterUrl());
        assertTrue(client.getConfiguration().getAutoConfigure());
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
        assertEquals(KUBERNETES_MASTER_URL, client.getConfiguration().getMasterUrl());
        assertTrue(client.getConfiguration().getAutoConfigure());
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
        assertTrue(client.getConfiguration().getAutoConfigure());
        assertEquals("http://example.com/", client.getConfiguration().getMasterUrl());
    }

    @Test
    @Issue("JENKINS-70416")
    public void autoConfigWithAuth() throws Exception {
        System.setProperty(KUBERNETES_NAMESPACE_FILE, "src/test/resources/kubenamespace");
        StringCredentialsImpl tokenCredential = new StringCredentialsImpl(CredentialsScope.GLOBAL, "sa-token", "some credentials", Secret.fromString("sa-token"));
        SystemCredentialsProvider.getInstance().getCredentials().add(tokenCredential);
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter(null, null, "sa-token", false);
        KubernetesClient client = factory.createClient();
        assertEquals("test-namespace", client.getNamespace());
        assertEquals(HTTP_PROXY, client.getConfiguration().getHttpProxy());
        assertEquals(HTTPS_PROXY, client.getConfiguration().getHttpsProxy());
        assertArrayEquals(new String[] { NO_PROXY }, client.getConfiguration().getNoProxy());
        assertEquals(PROXY_USERNAME, client.getConfiguration().getProxyUsername());
        assertEquals(PROXY_PASSWORD, client.getConfiguration().getProxyPassword());
        assertFalse(client.getConfiguration().getAutoConfigure());
        assertEquals(KUBERNETES_MASTER_URL, client.getConfiguration().getMasterUrl());
        assertEquals("sa-token", client.getConfiguration().getOauthToken());
    }

    @Test
    @Issue("JENKINS-70563")
    public void jenkinsProxyConfiguration() throws KubernetesAuthException, MalformedURLException {

        j.jenkins.setProxy(new ProxyConfiguration("proxy.com", 123, PROXY_USERNAME, PROXY_PASSWORD, "*acme.com\n*acme*.com\n*.example.com|192.168.*"));
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter("http://acme.com", null, null, null, false, 15, 5, 32, true);
        try(KubernetesClient client = factory.createClient()) {
            assertNull(HttpClientUtils.getProxyUrl(client.getConfiguration()));
        }

        j.jenkins.setProxy(new ProxyConfiguration("proxy.com", 123, PROXY_USERNAME, PROXY_PASSWORD, "*acme.com"));
        factory = new KubernetesFactoryAdapter("http://acme.com", null, null, null, false, 15, 5, 32, true);
        try(KubernetesClient client = factory.createClient()) {
            assertNull(HttpClientUtils.getProxyUrl(client.getConfiguration()));
        }
        factory = new KubernetesFactoryAdapter("http://k8s.acme.com", null, null, null, false, 15, 5, 32, true);
        try(KubernetesClient client = factory.createClient()) {
            assertNull(HttpClientUtils.getProxyUrl(client.getConfiguration()));
        }

        j.jenkins.setProxy(new ProxyConfiguration("proxy.com", 123, PROXY_USERNAME, PROXY_PASSWORD, "*.acme.com"));
        factory = new KubernetesFactoryAdapter("http://acme.com", null, null, null, false, 15, 5, 32, true);
        try(KubernetesClient client = factory.createClient()) {
            assertNotNull(HttpClientUtils.getProxyUrl(client.getConfiguration()));
        }
        factory = new KubernetesFactoryAdapter("http://k8s.acme.com", null, null, null, false, 15, 5, 32, true);
        try(KubernetesClient client = factory.createClient()) {
            assertNull(HttpClientUtils.getProxyUrl(client.getConfiguration()));
        }
    }
}
