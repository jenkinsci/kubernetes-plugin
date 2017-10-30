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

public class EmptyDirWorkspaceVolume extends WorkspaceVolume {

    private static final String DEFAULT_MEDIUM = "";
    private static final String MEMORY_MEDIUM = "Memory";

    @CheckForNull
    private Boolean memory;

    @DataBoundConstructor
    public EmptyDirWorkspaceVolume(Boolean memory) {
        this.memory = memory;
    }

    public String getMedium() {
        return getMemory() ? MEMORY_MEDIUM : DEFAULT_MEDIUM;
    }

    @Nonnull
    public Boolean getMemory() {
        return memory != null && memory;
    }

    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder().withName(volumeName).withNewEmptyDir().endEmptyDir().build();
    }

    @Extension
    @Symbol("emptyDirWorkspaceVolume")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {
        @Override
        public String getDisplayName() {
            return "Empty Dir Workspace Volume";
        }
    }
}