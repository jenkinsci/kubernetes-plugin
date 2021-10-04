/*
 * Copyright (C) 2017 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ContainerLogStep extends Step implements Serializable {
    private static final long serialVersionUID = 5588861066775717487L;

    private final String name;
    private boolean returnLog = false;
    private int tailingLines = 0;
    private int sinceSeconds = 0;
    private int limitBytes = 0;

    @DataBoundConstructor
    public ContainerLogStep(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setReturnLog(boolean returnLog) {
        this.returnLog = returnLog;
    }

    public boolean isReturnLog() {
        return returnLog;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new ContainerLogStepExecution(this, context);
    }

    public int getTailingLines() {
        return tailingLines;
    }

    @DataBoundSetter
    public void setTailingLines(int tailingLines) {
        this.tailingLines = tailingLines;
    }

    public int getSinceSeconds() {
        return sinceSeconds;
    }

    @DataBoundSetter
    public void setSinceSeconds(int sinceSeconds) {
        this.sinceSeconds = sinceSeconds;
    }

    public int getLimitBytes() {
        return limitBytes;
    }

    @DataBoundSetter
    public void setLimitBytes(int limitBytes) {
        this.limitBytes = limitBytes;
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "containerLog";
        }

        @Override
        public String getDisplayName() {
            return "Get container log from Kubernetes";
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(Node.class, FilePath.class, TaskListener.class)));
        }
    }
}
