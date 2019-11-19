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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;

import java.util.Objects;

public class PersistentVolumeClaimWorkspaceVolume extends WorkspaceVolume {
    private String claimName;
    @CheckForNull
    private Boolean readOnly;

    @DataBoundConstructor
    public PersistentVolumeClaimWorkspaceVolume(String claimName, Boolean readOnly) {
        this.claimName = claimName;
        this.readOnly = readOnly;
    }

    public String getClaimName() {
        return claimName;
    }

    @Nonnull
    public Boolean getReadOnly() {
        return readOnly != null && readOnly;
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim()
                    .withClaimName(getClaimName())
                    .withReadOnly(getReadOnly())
                .and()
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersistentVolumeClaimWorkspaceVolume that = (PersistentVolumeClaimWorkspaceVolume) o;
        return Objects.equals(claimName, that.claimName) &&
                Objects.equals(readOnly, that.readOnly);
    }

    @Override
    public int hashCode() {
        return Objects.hash(claimName, readOnly);
    }

    @Extension
    @Symbol("persistentVolumeClaimWorkspaceVolume")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {
        @Override
        public String getDisplayName() {
            return "Persistent Volume Claim Workspace Volume";
        }
    }
}