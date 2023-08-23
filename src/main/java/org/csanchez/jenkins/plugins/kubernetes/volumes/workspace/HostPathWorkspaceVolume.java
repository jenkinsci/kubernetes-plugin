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

package org.csanchez.jenkins.plugins.kubernetes.volumes.workspace;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "Serialization happens exclusively through XStream and not Java Serialization.")
public class HostPathWorkspaceVolume extends WorkspaceVolume {
    private String hostPath;

    @DataBoundConstructor
    public HostPathWorkspaceVolume(String hostPath) {
        this.hostPath = hostPath;
    }

    public Volume buildVolume(String volumeName, String podName) {
        return new VolumeBuilder() //
                .withName(volumeName) //
                .withNewHostPath().withPath(getHostPath()).endHostPath() //
                .build();
    }

    public String getHostPath() {
        return hostPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostPathWorkspaceVolume that = (HostPathWorkspaceVolume) o;
        return Objects.equals(hostPath, that.hostPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostPath);
    }

    @Extension
    @Symbol("hostPathWorkspaceVolume")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {
        @Override
        public String getDisplayName() {
            return "Host Path Workspace Volume";
        }
    }
}