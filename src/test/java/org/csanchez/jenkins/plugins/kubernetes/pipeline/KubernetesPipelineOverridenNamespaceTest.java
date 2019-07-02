package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.testingNamespace;
import static org.junit.Assert.assertNotNull;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class KubernetesPipelineOverridenNamespaceTest extends AbstractKubernetesPipelineTest {

    @Test
    public void runWithCloudOverriddenNamespace() throws Exception {
        String overriddenNamespace = testingNamespace + "-overridden-namespace";
        cloud.setNamespace(overriddenNamespace);
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        if (client.namespaces().withName(overriddenNamespace).get() == null) {
            client.namespaces().createOrReplace(
                    new NamespaceBuilder().withNewMetadata().withName(overriddenNamespace).endMetadata().build());
        }

        assertNotNull(createJobThenScheduleRun());

        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains(overriddenNamespace, b);
    }

    /**
     * Step namespace should have priority over anything else.
     */
    @Test
    public void runWithStepOverriddenNamespace() throws Exception {
        String overriddenNamespace = testingNamespace + "-overridden-namespace";
        String stepNamespace = testingNamespace + "-overridden-namespace2";
        cloud.setNamespace(overriddenNamespace);
        KubernetesClient client = cloud.connect();
        // Run in our own testing namespace
        if (client.namespaces().withName(stepNamespace).get() == null) {
            client.namespaces().createOrReplace(
                    new NamespaceBuilder().withNewMetadata().withName(stepNamespace).endMetadata().build());
        }

        Map<String, String> env = new HashMap<>();
        env.put("OVERRIDDEN_NAMESPACE", stepNamespace);
        assertNotNull(createJobThenScheduleRun(env));
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains(stepNamespace, b);
    }
}
