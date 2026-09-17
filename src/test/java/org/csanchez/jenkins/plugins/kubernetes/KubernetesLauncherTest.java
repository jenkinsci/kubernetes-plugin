package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KubernetesLauncherTest {

    private static final String K8S_RESOURCE_QUOTA_CONFLICT =
            "Operation cannot be fulfilled on resourcequotas \"compute-resources\": the object has been modified; please apply your changes to the latest version and try again";

    private static final String OPENSHIFT_CLUSTER_RESOURCE_QUOTA_CONFLICT =
            "Operation cannot be fulfilled on clusterresourcequotas.quota.openshift.io \"dno--notterminating\": the object has been modified; please apply your changes to the latest version and try again";

    @Test
    void isResourceQuotaUpdateConflict_kubernetesResourceQuota() {
        assertThat(KubernetesLauncher.isResourceQuotaUpdateConflict(409, K8S_RESOURCE_QUOTA_CONFLICT), is(true));
    }

    @Test
    void isResourceQuotaUpdateConflict_openshiftClusterResourceQuota() {
        assertThat(
                KubernetesLauncher.isResourceQuotaUpdateConflict(409, OPENSHIFT_CLUSTER_RESOURCE_QUOTA_CONFLICT),
                is(true));
    }

    @Test
    void isResourceQuotaUpdateConflict_not409() {
        assertThat(KubernetesLauncher.isResourceQuotaUpdateConflict(403, K8S_RESOURCE_QUOTA_CONFLICT), is(false));
    }

    @Test
    void isResourceQuotaUpdateConflict_nullMessage() {
        assertThat(KubernetesLauncher.isResourceQuotaUpdateConflict(409, null), is(false));
    }

    @Test
    void isResourceQuotaUpdateConflict_unrelated409() {
        assertThat(KubernetesLauncher.isResourceQuotaUpdateConflict(409, "pods \"my-pod\" already exists"), is(false));
    }

    /**
     * Lifecycle of the per-agent secret holding the agent connection secret, which the agent container reads through a
     * {@code secretKeyRef} instead of having it inlined in clear text in the pod spec.
     */
    @Nested
    @EnableKubernetesMockClient(crud = true)
    class AgentSecret {

        private static final String NAMESPACE = "jenkins";
        private static final String POD_NAME = "test-agent-abc12";
        private static final String JNLP_MAC = "d1e2a3d4b5e6e7f8";

        KubernetesClient client;

        private Secret secret() {
            return client.secrets()
                    .inNamespace(NAMESPACE)
                    .withName(PodTemplateBuilder.agentSecretName(POD_NAME))
                    .get();
        }

        private String decode(Secret secret) {
            return new String(
                    Base64.getDecoder().decode(secret.getData().get(PodTemplateBuilder.JENKINS_SECRET_KEY)),
                    StandardCharsets.UTF_8);
        }

        @Test
        void createdWithTheConnectionSecret() {
            KubernetesLauncher.createAgentSecret(client, NAMESPACE, POD_NAME, JNLP_MAC);

            Secret secret = secret();
            assertNotNull(secret, "secret should have been created");
            assertEquals("Opaque", secret.getType());
            assertEquals(JNLP_MAC, decode(secret));
        }

        @Test
        void createIsIdempotentAcrossLaunchAttempts() {
            KubernetesLauncher.createAgentSecret(client, NAMESPACE, POD_NAME, "stale");
            // e.g. the controller was interrupted between creating the secret and creating the pod
            KubernetesLauncher.createAgentSecret(client, NAMESPACE, POD_NAME, JNLP_MAC);

            assertEquals(JNLP_MAC, decode(secret()));
        }

        @Test
        void ownedByThePodOnceItExists() {
            KubernetesLauncher.createAgentSecret(client, NAMESPACE, POD_NAME, JNLP_MAC);
            assertThat(secret().getMetadata().getOwnerReferences(), is(empty()));

            Pod pod = client.pods()
                    .inNamespace(NAMESPACE)
                    .resource(new PodBuilder()
                            .withNewMetadata()
                            .withName(POD_NAME)
                            .withNamespace(NAMESPACE)
                            .withUid("2c9d8c0e-0e0e-4a4a-8b8b-1f1f1f1f1f1f")
                            .endMetadata()
                            .build())
                    .create();

            KubernetesLauncher.adoptAgentSecret(client, NAMESPACE, pod);

            List<OwnerReference> ownerReferences = secret().getMetadata().getOwnerReferences();
            assertThat(ownerReferences.stream().map(OwnerReference::getName).toList(), contains(POD_NAME));
            OwnerReference ownerReference = ownerReferences.get(0);
            assertEquals("Pod", ownerReference.getKind());
            assertEquals("v1", ownerReference.getApiVersion());
            assertEquals(pod.getMetadata().getUid(), ownerReference.getUid());
            assertTrue(ownerReference.getController());
        }

        @Test
        void deletedWhenThePodCouldNotBeCreated() {
            KubernetesLauncher.createAgentSecret(client, NAMESPACE, POD_NAME, JNLP_MAC);
            assertNotNull(secret());

            KubernetesLauncher.deleteAgentSecret(client, NAMESPACE, POD_NAME);

            assertThat(secret(), is(nullValue()));
        }

        @Test
        void notCreatedWhenTheFeatureIsDisabled() {
            boolean orig = PodTemplateBuilder.SECRET_VIA_SECRET_KEY_REF;
            PodTemplateBuilder.SECRET_VIA_SECRET_KEY_REF = false;
            try {
                KubernetesLauncher.createAgentSecret(client, NAMESPACE, POD_NAME, JNLP_MAC);
                assertNull(secret());
            } finally {
                PodTemplateBuilder.SECRET_VIA_SECRET_KEY_REF = orig;
            }
        }
    }
}
