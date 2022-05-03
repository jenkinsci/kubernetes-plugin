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

import static org.junit.Assert.assertEquals;

import java.util.function.Consumer;

import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Always;
import org.junit.Assert;
import org.junit.Test;

public class KubernetesClientProviderTest {

    @Test
    public void testGetValidity() {
        KubernetesCloud cloud = new KubernetesCloud("foo");
        // changes to these properties should trigger different validity value
        checkValidityChanges(cloud,
                c -> c.setServerUrl("https://server:443"),
                c -> c.setNamespace("blue"),
                c -> c.setServerCertificate("cert"),
                c -> c.setCredentialsId("secret"),
                c -> c.setSkipTlsVerify(true),
                c -> c.setConnectTimeout(46),
                c -> c.setReadTimeout(43),
                c -> c.setMaxRequestsPerHost(47),
                c -> c.setUseJenkinsProxy(true)
        );

        // changes to these properties should not trigger different validity value
        checkValidityDoesNotChange(cloud,
                c -> c.setPodLabels(PodLabel.listOf("foo", "bar")),
                c -> c.setJenkinsUrl("https://localhost:8081"),
                c -> c.setJenkinsTunnel("https://jenkins.cluster.svc"),
                c -> c.setPodRetention(new Always()),
                c -> c.setWebSocket(true),
                c -> c.setRetentionTimeout(52),
                c -> c.setDirectConnection(true)
        );

        // verify stability
        assertEquals(KubernetesClientProvider.getValidity(cloud), KubernetesClientProvider.getValidity(cloud));
    }

    private void checkValidityChanges(KubernetesCloud cloud, Consumer<KubernetesCloud>... mutations) {
        checkValidity(cloud, Assert::assertNotEquals, mutations);
    }

    private void checkValidityDoesNotChange(KubernetesCloud cloud, Consumer<KubernetesCloud>... mutations) {
        checkValidity(cloud, Assert::assertEquals, mutations);
    }

    private void checkValidity(KubernetesCloud cloud, ValidityAssertion validityAssertion, Consumer<KubernetesCloud>... mutations) {
        int v = KubernetesClientProvider.getValidity(cloud);
        int count = 1;
        for (Consumer<KubernetesCloud> mut : mutations) {
            mut.accept(cloud);
            int after = KubernetesClientProvider.getValidity(cloud);
            validityAssertion.doAssert("change #" + count++ + " of " + mutations.length, v, after);
            v = after;
        }
    }

    interface ValidityAssertion {
        void doAssert(String message, int before, int after);
    }

}