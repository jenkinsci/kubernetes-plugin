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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

import java.util.Objects;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "Serialization happens exclusively through XStream and not Java Serialization.")
public class NfsWorkspaceVolume extends WorkspaceVolume {
    private String serverAddress;
    private String serverPath;
    @CheckForNull
    private Boolean readOnly;

    @DataBoundConstructor
    public NfsWorkspaceVolume(String serverAddress, String serverPath, Boolean readOnly) {
        this.serverAddress = serverAddress;
        this.serverPath = serverPath;
        this.readOnly = readOnly;
    }

    public Volume buildVolume(String volumeName, String podName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewNfs(getServerPath(), getReadOnly(), getServerAddress())
                .build();
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getServerPath() {
        return serverPath;
    }

    @NonNull
    public Boolean getReadOnly() {
        return readOnly != null && readOnly;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NfsWorkspaceVolume that = (NfsWorkspaceVolume) o;
        return Objects.equals(serverAddress, that.serverAddress) &&
                Objects.equals(serverPath, that.serverPath) &&
                Objects.equals(readOnly, that.readOnly);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverAddress, serverPath, readOnly);
    }

    @Extension
    @Symbol("nfsWorkspaceVolume")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "NFS Workspace Volume";
        }
    }
}
