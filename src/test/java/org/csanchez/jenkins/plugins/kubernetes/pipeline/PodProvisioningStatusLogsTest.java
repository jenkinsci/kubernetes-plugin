package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Result;
import org.junit.jupiter.api.Test;

class PodProvisioningStatusLogsTest extends AbstractKubernetesPipelineTest {

    @Test
    void podStatusErrorLogs() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        // pod not schedulable
        // build never finishes, so just checking the message and killing
        r.waitForMessage("Pod [Pending][Unschedulable] 0/1 nodes are available", b);
        b.doKill();
        r.waitUntilNoActivity();
    }

    @Test
    void podStatusNoErrorLogs() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        // regular logs when starting containers
        r.assertLogContains("Container [jnlp] waiting [ContainerCreating]", b);
        r.assertLogContains("Pod [Pending][ContainersNotReady] containers with unready status: [shell jnlp]", b);
    }

    @Test
    void containerStatusErrorLogs() throws Exception {
        assertNotNull(createJobThenScheduleRun());
        r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        // error starting container
        r.assertLogContains("Container [shell] terminated [StartError]", b);
        r.assertLogContains("exec: \"oops\": executable file not found", b);
        r.assertLogContains("Pod [Running][ContainersNotReady] containers with unready status: [shell]", b);
    }
}
