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

package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

/**
 * Callback for Kubernetes cloud provision
 * 
 * @since 0.13
 */
class ProvisioningCallback implements Callable<Node> {

    @Nonnull
    private final KubernetesCloud cloud;
    @Nonnull
    private final PodTemplate t;
    @CheckForNull
    private final Label label;

    public ProvisioningCallback(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate t, @CheckForNull Label label) {
        this.cloud = cloud;
        this.t = t;
        this.label = label;
    }

    public Node call() throws Exception {
        RetentionStrategy retentionStrategy;
        if (t.getIdleMinutes() == 0) {
            retentionStrategy = new OnceRetentionStrategy(cloud.getRetentionTimeout());
        } else {
            retentionStrategy = new CloudRetentionStrategy(t.getIdleMinutes());
        }

        final PodTemplate unwrappedTemplate = PodTemplateUtils.unwrap(cloud.getTemplate(label),
                cloud.getDefaultsProviderTemplate(), cloud.getTemplates());
        return new KubernetesSlave(unwrappedTemplate, unwrappedTemplate.getName(), cloud.name,
                unwrappedTemplate.getLabel(), retentionStrategy);
    }

}
