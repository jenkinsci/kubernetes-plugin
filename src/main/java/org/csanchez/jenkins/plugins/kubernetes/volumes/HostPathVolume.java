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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "Serialization happens exclusively through XStream and not Java Serialization.")
public class HostPathVolume extends PodVolume {
    private String mountPath;
    private String hostPath;

    @CheckForNull
    private Boolean readOnly;

    @DataBoundConstructor
    public HostPathVolume(String hostPath, String mountPath, Boolean readOnly) {
        this.hostPath = hostPath;
        this.mountPath = mountPath;
        this.readOnly = readOnly;
    }

    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder() //
                .withName(volumeName) //
                .withNewHostPath().withPath(getHostPath()).endHostPath() //
                .build();
    }

    public String getMountPath() {
        return mountPath;
    }

    public String getHostPath() {
        return hostPath;
    }

    @NonNull
    public Boolean getReadOnly() {
        return readOnly != null && readOnly;
    }

    @Extension
    @Symbol("hostPathVolume")
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        public String getDisplayName() {
            return "Host Path Volume";
        }
    }

    @Override
    public String toString() {
        return "HostPathVolume [mountPath=" + mountPath + ", hostPath=" + hostPath + "]";
    }
}
