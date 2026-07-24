/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;

import hudson.model.Result;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContainerListenDecoratorTest extends AbstractKubernetesPipelineTest {

    @BeforeEach
    void beforeEach() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
        cloud.setActiveContainers(true);
        logs.record(ContainerListenDecorator.class, Level.FINE);
    }

    @Test
    void activeContainerSmokes() throws Exception {
        r.assertLogContains(
                "go version go1.6.3", r.assertBuildStatusSuccess(r.waitForCompletion(createJobThenScheduleRun())));
    }

    @Test
    void activeContainerInterruptedPod() throws Exception {
        createJobThenScheduleRun();
        r.waitForMessage("starting to sleep", b);
        b.getExecutor().interrupt();
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        r.assertLogContains("shut down gracefully", b);
    }

    @Test
    void activeContainerPassiveSidecar() throws Exception {
        r.assertLogContains(
                "Hello, world!", r.assertBuildStatusSuccess(r.waitForCompletion(createJobThenScheduleRun())));
    }

    @Test
    void activeContainerCustomWorkingDir() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(createJobThenScheduleRun()));
        r.assertLogContains("running in container in /else$where/workspace/", b);
    }

    // TODO missing test coverage (compare https://github.com/jenkinsci/kubernetes-plugin/pull/2837/commits):
    // · using cat rather than sleep
    // · environment variables from agent pod vs. container
    //   (KubernetesDeclarativeAgentTest#declarative & ContainerExecDecoratorPipelineTest#containerEnvironmentIsHonored)
    // · BASH_COMPLIANT_ENV_VAR (KubernetesPipelineTest#runWithEnvVariablesInContext)
    // · nonexistent container name (KubernetesDeclarativeAgentTest.declarativeWithNestedExplicitInheritance)
    // · unpatched container
    // · restart controller during or between sh steps
    // · container step used in addition to defaultContainer (so nested steps)
    // · command masking (ContainerExecDecoratorPipelineTest.docker)
    // · nondefault shell (KubernetesPipelineTest#runInPodWithDifferentShell but a passing build)
    // · fallback on Windows
    // · parallel sh steps
    // · LAUNCH_DIAGNOSTICS support for interruption & parallelization (may need to make listen loop asynch)

}
