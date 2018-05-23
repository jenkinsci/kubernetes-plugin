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
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;

/**
 * Environment variables created from kubernetes secrets.
 * 
 * @since 0.13
 */
public class SecretEnvVar extends TemplateEnvVar {

    private static final long serialVersionUID = -7481523353781581248L;

    private String secretName;
    private String secretKey;

    @DataBoundConstructor
    public SecretEnvVar(String key, String secretName, String secretKey) {
        super(key);
        this.secretName = secretName;
        this.secretKey = secretKey;
    }

    @Override
    public EnvVar buildEnvVar() {
        return new EnvVarBuilder() //
                .withName(getKey()) //
                .withValueFrom(new EnvVarSourceBuilder() //
                        .withSecretKeyRef(
                                new SecretKeySelectorBuilder().withKey(secretKey).withName(secretName).build()) //
                        .build()) //
                .build();
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public String toString() {
        return "SecretEnvVar [secretName=" + secretName + ", secretKey=" + secretKey + ", getKey()=" + getKey() + "]";
    }

    @Extension
    @Symbol("secretEnvVar")
    public static class DescriptorImpl extends Descriptor<TemplateEnvVar> {
        @Override
        public String getDisplayName() {
            return "Environment Variable from Secret";
        }
    }
}
