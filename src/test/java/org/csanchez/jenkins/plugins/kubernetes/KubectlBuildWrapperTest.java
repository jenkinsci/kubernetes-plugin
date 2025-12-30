package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.deletePods;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.getLabels;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Result;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.AbstractKubernetesPipelineTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KubectlBuildWrapperTest extends AbstractKubernetesPipelineTest {

    @BeforeEach
    void beforeEach() throws Exception {
        deletePods(cloud.connect(), getLabels(cloud, this, name), false);
        assertNotNull(createJobThenScheduleRun());
        UsernamePasswordCredentialsImpl creds = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "id", "some credentials", "username", "password");
        SystemCredentialsProvider.getInstance().getCredentials().add(creds);
    }

    @Test
    void kubectlBuildWrapper_missingCredentials() throws Exception {
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        r.assertLogContains("No credentials found for id \"abcd\"", b);
    }

    @Test
    void kubectlBuildWrapper_invalidCredentials() throws Exception {
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        r.assertLogContains("Unable to connect to the server", b);
    }
}
