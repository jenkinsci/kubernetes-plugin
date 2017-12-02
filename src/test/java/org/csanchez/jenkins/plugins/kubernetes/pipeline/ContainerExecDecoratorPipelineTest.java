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

import static org.junit.Assert.*;

import org.apache.commons.compress.utils.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.PrivateKeySource;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

public class ContainerExecDecoratorPipelineTest extends AbstractKubernetesPipelineTest {

    @Issue({ "JENKINS-47225", "JENKINS-42582" })
    @Test
    public void sshagent() throws Exception {
        PrivateKeySource source = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(
                new String(IOUtils.toByteArray(getClass().getResourceAsStream("id_rsa"))));
        BasicSSHUserPrivateKey credentials = new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL,
                "ContainerExecDecoratorPipelineTest-sshagent", "bob", source, "secret_passphrase", "test credentials");
        SystemCredentialsProvider.getInstance().getCredentials().add(credentials);

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "sshagent");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("sshagent.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.waitForCompletion(b);
        r.assertLogContains("Identity added:", b);
        //check that we don't accidentally start exporting sensitive info to the log
        r.assertLogNotContains("secret_passphrase", b);
    }
}
