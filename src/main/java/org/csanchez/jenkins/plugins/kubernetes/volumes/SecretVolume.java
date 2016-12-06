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

package org.csanchez.jenkins.plugins.kubernetes.volumes;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

public class SecretVolume extends PodVolume {

    private String mountPath;
    private String secretName;

    @DataBoundConstructor
    public SecretVolume(String mountPath, String secretName) {
        this.mountPath = mountPath;
        this.secretName = secretName;
    }

    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewSecret().withSecretName(getSecretName()).endSecret()
                .build();
    }

    public String getSecretName() {
        return secretName;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    @Extension
    @Symbol("secretVolume")
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        public String getDisplayName() {
            return "Secret Volume";
        }
    }
}