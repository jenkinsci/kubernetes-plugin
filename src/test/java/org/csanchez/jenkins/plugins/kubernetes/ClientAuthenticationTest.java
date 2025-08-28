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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.net.URL;
import java.util.logging.Logger;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.MockedStatic;

public class ClientAuthenticationTest {

    private static final Logger LOGGER = Logger.getLogger(ClientAuthenticationTest.class.getName());

    @Issue("JENKINS-76047")
    @Test
    public void testConnectCallsCreateClient() throws Exception {
        KubernetesClient mockClient = mock(KubernetesClient.class);
        when(mockClient.getMasterUrl()).thenReturn(new URL("http://localhost:9999/"));

        try (MockedStatic<KubernetesClientProvider> mocked = mockStatic(KubernetesClientProvider.class)) {
            mocked.when(() -> KubernetesClientProvider.createClient(any()))
                    .thenThrow(new KubernetesAuthException("auth fail"))
                    .thenReturn(mockClient);

            KubernetesCloud cloud = new KubernetesCloud("test");
            cloud.connect();

            mocked.verify(() -> KubernetesClientProvider.createClient(any()), atLeast(2));
        }
    }
}
