/*
 * Copyright 2020 CloudBees, Inc.
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

import java.util.logging.Level;
import jenkins.agents.WebSocketAgents;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

@Issue("JEP-222")
public class WebSocketTest extends AbstractKubernetesPipelineTest {

    @Before
    public void setUp() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
        r.jenkins.setSlaveAgentPort(-1);
        cloud.setWebSocket(true);
        logs.record(WebSocketAgents.class, Level.FINE);
    }

    @Test
    public void webSocketAgent() throws Exception {
        r.assertBuildStatusSuccess(r.waitForCompletion(createJobThenScheduleRun()));
    }

}
