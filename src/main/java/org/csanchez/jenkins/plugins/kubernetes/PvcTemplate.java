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

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.EnumConverter;
import io.fabric8.kubernetes.api.model.*;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import java.io.Serializable;
import java.util.Collections;


public class PvcTemplate extends AbstractDescribableImpl<PvcTemplate> implements Serializable {

    private String mountPath;
    private String claimName;
    private String storageClass;
    private Integer storageCapacity;
    private StorageUnit storageUnit;
    private AccessMode accessMode;

    @DataBoundConstructor
    public PvcTemplate(
            String mountPath, String claimName, String storageClass,
            Integer storageCapacity, StorageUnit storageUnit, AccessMode accessMode
    ) {
        this.mountPath = mountPath;
        this.claimName = claimName;
        this.storageClass = storageClass;
        this.storageCapacity = storageCapacity;
        this.storageUnit = storageUnit;
        this.accessMode = accessMode;
    }

    public static enum StorageUnit {
        Ei, Pi, Ti, Gi, Mi, Ki;

        public String getName() {
            return this.name();
        }

        static {
            Stapler.CONVERT_UTILS.register(new EnumConverter(), AccessMode.class);
        }
    }

    public static enum AccessMode {
        ReadWriteOnce, ReadOnlyMany, ReadWriteMany;

        public String getName() {
            return this.name();
        }

        static {
            Stapler.CONVERT_UTILS.register(new EnumConverter(), AccessMode.class);
        }
    }

    public String getMountPath() {
        return mountPath;
    }

    public String getClaimName() {
        return claimName;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public Integer  getStorageCapacity() {
        return storageCapacity;
    }

    public StorageUnit getStorageUnit() {
        return storageUnit;
    }

    public String getStorageRequest() {
        return String.format("%d%s", getStorageCapacity(), getStorageUnit());
    }

    public AccessMode getAccessMode() {
        return accessMode;
    }

    public PersistentVolumeClaim buildPVC(String claimName) {
        return new PersistentVolumeClaimBuilder()
            // metadata
            .withNewMetadata()
                .withName(getClaimName() + "-" + claimName + "-pvc")
            .endMetadata()
            // spec
            .withNewSpec()
                .withAccessModes(getAccessMode().getName())
                .withStorageClassName(getStorageClass())
                .withNewResources()
                    .withRequests(
                            Collections.singletonMap("storage", new Quantity(getStorageRequest()))
                    )
                .endResources()
            .endSpec()
            .build();
    }

    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewPersistentVolumeClaim(getClaimName() + "-" + volumeName + "-pvc", false)
                .build();
    }

    @Extension
    @Symbol("pvcTemplate")
    public static class DescriptorImpl extends Descriptor<PvcTemplate> {
        @Override
        public String getDisplayName() {
            return "Persistent Volume Claim Template";
        }
    }
}