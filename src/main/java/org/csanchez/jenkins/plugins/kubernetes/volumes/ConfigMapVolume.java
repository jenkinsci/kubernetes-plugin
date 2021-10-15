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
import hudson.Util;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import org.kohsuke.stapler.DataBoundSetter;


public class ConfigMapVolume extends PodVolume {
    private String mountPath;
    private String subPath;
    private String configMapName;
    private Boolean optional;

    @DataBoundConstructor
    public ConfigMapVolume(String mountPath, String configMapName, Boolean optional) {
        this.mountPath = mountPath;
        this.configMapName = configMapName;
        this.optional = optional;
    }


    @Override
    public Volume buildVolume(String volumeName) {
        return new VolumeBuilder()
                .withName(volumeName)
                .withNewConfigMap()
                    .withName(getConfigMapName())
                    .withOptional(getOptional())
                .and()
                .build();
    }

    public String getConfigMapName() {
        return configMapName;
    }

    @Override
    public String getMountPath() {
        return mountPath;
    }

    public Boolean getOptional() {
        return optional;
    }
    
    public String getSubPath() {
        return subPath;
    }
    
    @DataBoundSetter
    public void setSubPath(String subPath) {
        this.subPath = Util.fixEmpty(subPath);
    }

    protected Object readResolve() {
        this.subPath = Util.fixEmpty(subPath);
        return this;
    }
    
    @Extension
    @Symbol("configMapVolume")
    public static class DescriptorImpl extends Descriptor<PodVolume> {
        @Override
        public String getDisplayName() {
            return "Config Map Volume";
        }
    }
}
