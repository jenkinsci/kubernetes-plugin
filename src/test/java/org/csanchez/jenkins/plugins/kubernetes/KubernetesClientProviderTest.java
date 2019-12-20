package org.csanchez.jenkins.plugins.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Before;
import org.junit.Test;
import static io.fabric8.kubernetes.client.Config.*;
import static org.junit.Assert.assertEquals;

/**
 * Test the creation of client with httpsProxy
 */
public class KubernetesClientProviderTest {
    private static final String HTTPS_PROXY = "https://example.com:123";
    private static final String DEFAULT_HTTPS_PROXY = "https://example.com:1234";

    @Before
    public void injectEnvironmentVariables() {
        System.setProperty(KUBERNETES_HTTPS_PROXY, DEFAULT_HTTPS_PROXY);
    }
    @Test
    public void testHttpsProxyInjection() throws Exception {
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter("http://example.com", HTTPS_PROXY, null, null, null, false, 1, 1, 1);
        KubernetesClient client = factory.createClient();
        assertEquals(HTTPS_PROXY, client.getConfiguration().getHttpsProxy());
    }

    @Test
    public void testDefaultHttpsProxyInjection() throws Exception {
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter("http://example.com", null, null, null, null, false, 1, 1, 1);
        KubernetesClient client = factory.createClient();
        assertEquals(DEFAULT_HTTPS_PROXY, client.getConfiguration().getHttpsProxy());
    }

    @Test
    public void testEmptyHttpsProxyInjection() throws Exception {
        KubernetesFactoryAdapter factory = new KubernetesFactoryAdapter("http://example.com", "", null, null, null, false, 1, 1, 1);
        KubernetesClient client = factory.createClient();
        assertEquals(DEFAULT_HTTPS_PROXY, client.getConfiguration().getHttpsProxy());
    }
}
