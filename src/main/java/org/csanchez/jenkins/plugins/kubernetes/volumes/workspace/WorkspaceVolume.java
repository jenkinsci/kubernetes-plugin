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

import java.io.Serializable;

import hudson.model.AbstractDescribableImpl;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Base class for all Kubernetes workspace volume types
 */
public abstract class WorkspaceVolume extends AbstractDescribableImpl<WorkspaceVolume> implements Serializable {

    private static final long serialVersionUID = 5367004248055474414L;

    public static WorkspaceVolume merge(WorkspaceVolume parentWorkspaceVolume, WorkspaceVolume templateVolume) {
        // Only override parent if differ from the default
        return getDefault().equals(templateVolume) ? parentWorkspaceVolume : templateVolume;
    }

    public static WorkspaceVolume getDefault() {
        return new EmptyDirWorkspaceVolume(false);
    }

    // Builds a Volume model with the given name.require podName to generate pvc name
    public Volume buildVolume(String volumeName, String podName){
        return buildVolume(volumeName);
    };

    // Builds a Volume model with the given name.
    @Deprecated
    public Volume buildVolume(String volumeName){
        throw new UnsupportedOperationException("could not build volume without podName");
    }

    /**
     * Create the workspace volume
     * @param client Kubernetes client
     * @param podMetaData Kubernetes pod metadata
     * @return the created persistent volume claim
     */
    public PersistentVolumeClaim createVolume(KubernetesClient client, ObjectMeta podMetaData) {
        return null;
    }
}
