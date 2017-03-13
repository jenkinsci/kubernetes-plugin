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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;
import static org.hamcrest.Matchers.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assume;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.util.StreamTaskListener;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

public class KubernetesTestUtil {

    private static final Logger LOGGER = Logger.getLogger(KubernetesTestUtil.class.getName());

    public static final String TESTING_NAMESPACE = "kubernetes-plugin-test";

    private static final String[] MINIKUBE_COMMANDS = new String[] { "minikube", "/usr/local/bin/minikube" };
    private static String ip = null;

    public static URL miniKubeUrl() {
        try {
            return new URL("https", miniKubeIp(), 8443, "");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static String miniKubeIp() {
        if (ip == null) {
            for (String cmd : MINIKUBE_COMMANDS) {
                String s = miniKubeIp(new Launcher.LocalLauncher(StreamTaskListener.NULL), cmd);
                if (s != null) {
                    ip = s.trim();
                    LOGGER.log(Level.INFO, "Using minikube at {0}", ip);
                    return ip;
                }
            }
            LOGGER.warning("Minikube was not found or is stopped");
            ip = "";
        }
        return ip;
    }

    private static String miniKubeIp(Launcher.LocalLauncher localLauncher, String cmd) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            localLauncher.decorateByEnv(new EnvVars("PATH", System.getenv("PATH") + ":/usr/local/bin")).launch()
                    .cmds(cmd, "ip").stdout(baos).join();
            String stdout = baos.toString().trim();

            // leave last line only, ie. when a new version is available it will print some info message
            int i = stdout.lastIndexOf("\n");
            String s;
            if (i > 0) {
                s = stdout.substring(i);
            } else {
                s = stdout;
            }
            // check that we got an ip and is valid
            new URL("http://" + s);
            return s;

        } catch (InterruptedException | IOException x) {
            return null;
        }
    }

    public static void assumeMiniKube() throws Exception {
        Assume.assumeThat("MiniKube working", miniKubeIp(), not(isEmptyOrNullString()));
    }

    public static KubernetesCloud setupCloud() throws UnrecoverableKeyException, CertificateEncodingException,
            NoSuchAlgorithmException, KeyStoreException, IOException {
        KubernetesCloud cloud = new KubernetesCloud("minikube");
        cloud.setServerUrl(miniKubeUrl().toExternalForm());
        cloud.setNamespace(TESTING_NAMESPACE);
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        client.namespaces().createOrReplace(
                new NamespaceBuilder().withNewMetadata().withName(TESTING_NAMESPACE).endMetadata().build());
        return cloud;
    }
}
