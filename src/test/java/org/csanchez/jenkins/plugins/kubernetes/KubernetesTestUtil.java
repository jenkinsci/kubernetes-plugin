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

import static org.hamcrest.Matchers.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Assume;

import hudson.Launcher;
import hudson.util.StreamTaskListener;

public class KubernetesTestUtil {

    private static String ip = null;

    public static String miniKubeIp() {
        if (ip == null) {
            Launcher.LocalLauncher localLauncher = new Launcher.LocalLauncher(StreamTaskListener.NULL);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                localLauncher.launch().cmds("minikube", "ip").stdout(baos).join();
                ip = baos.toString().trim();
            } catch (InterruptedException | IOException x) {
                ip = "";
            }
        }
        return ip;
    }

    public static void assumeMiniKube() throws Exception {
        Assume.assumeThat("MiniKube working", ip, not(isEmptyOrNullString()));
    }

}
