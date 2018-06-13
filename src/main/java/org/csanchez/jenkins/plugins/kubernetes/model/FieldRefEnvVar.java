/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

package org.csanchez.jenkins.plugins.kubernetes.model;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder;

/**
 * Environment variables created from kubernetes ObjectField.
 * 
 * @since 0.13
 */
public class FieldRefEnvVar extends TemplateEnvVar {

	private static final long serialVersionUID = -4518577838038721933L;
	private String fieldPath;

    @DataBoundConstructor
    public FieldRefEnvVar(String key, String fieldPath) {
        super(key);
        this.fieldPath = fieldPath;
    }

    @Override
    public EnvVar buildEnvVar() {
        return new EnvVarBuilder() //
                .withName(getKey()) //
                .withValueFrom(new EnvVarSourceBuilder() //
                        .withFieldRef(
                                new ObjectFieldSelectorBuilder().withFieldPath(fieldPath).build()) //
                        .build()) //
                .build();
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
        this.fieldPath = fieldPath;
    }


    @Override
    public String toString() {
        return "FieldRefEnvVar [fieldPath=" + fieldPath + ", getKey()=" + getKey() + "]";
    }

    @Extension
    @Symbol("fieldRefEnvVar")
    public static class DescriptorImpl extends Descriptor<TemplateEnvVar> {
        @Override
        public String getDisplayName() {
            return "Environment Variable from ObjectField";
        }
    }
}
