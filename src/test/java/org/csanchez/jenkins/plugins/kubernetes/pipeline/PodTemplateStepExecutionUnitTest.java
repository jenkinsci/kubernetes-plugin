/*
 * Copyright 2019 CloudBees, Inc.
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

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assertRegex;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

@For(PodTemplateStepExecution.class)
public class PodTemplateStepExecutionUnitTest {

    @Issue("JENKINS-57830")
    @Test public void labelify() {
        assertRegex(PodTemplateStepExecution.labelify("foo"), "foo-[a-z0-9]{5}");
        assertRegex(PodTemplateStepExecution.labelify("foo bar #3"), "foo_bar__3-[a-z0-9]{5}");
        assertRegex(PodTemplateStepExecution.labelify("This/Thing"), "This_Thing-[a-z0-9]{5}");
        assertRegex(PodTemplateStepExecution.labelify("/whatever"), "xwhatever-[a-z0-9]{5}");
        assertRegex(PodTemplateStepExecution.labelify("way-way-way-too-prolix-for-the-sixty-three-character-limit-in-kubernetes"), "xprolix-for-the-sixty-three-character-limit-in-kubernetes-[a-z0-9]{5}");
    }

}
