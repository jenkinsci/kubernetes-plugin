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

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertNotNull;

public class ContainerLogStepTest extends AbstractKubernetesPipelineTest {

    @Issue("JENKINS-46085")
    @Test
    public void getContainerLog() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("INFO: Handshaking", b);
        r.assertLogContains("INFO: Connected", b);
    }
}
