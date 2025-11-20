package org.csanchez.jenkins.plugins.kubernetes.casc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.jenkins.plugins.casc.misc.junit.jupiter.AbstractRoundTripTest;
import java.util.List;
import java.util.stream.Stream;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@ParameterizedClass(name = "{index}: {1}")
@MethodSource("permutations")
class EnvVarCasCTest extends AbstractRoundTripTest {

    @Parameter(0)
    private EnvVarStrategy strategy;

    @Parameter(1)
    private String resource;

    static Stream<Arguments> permutations() {
        return Stream.of(
                Arguments.of(new KeyValueEnvVarStrategy(), "keyValue"),
                Arguments.of(new SecretEnvVarStrategy(), "secret"));
    }

    @Override
    protected void assertConfiguredAsExpected(JenkinsRule r, String configContent) {
        KubernetesCloud cloud = r.jenkins.clouds.get(KubernetesCloud.class);
        assertNotNull(cloud);
        List<PodTemplate> templates = cloud.getTemplates();
        assertNotNull(templates);
        assertEquals(1, templates.size());
        PodTemplate podTemplate = templates.get(0);
        List<TemplateEnvVar> envVars = podTemplate.getEnvVars();
        assertEquals(1, envVars.size());
        strategy._verify(envVars.get(0));
    }

    @Override
    protected String configResource() {
        return "casc_envVar_" + resource + ".yaml";
    }

    @Override
    protected String stringInLogExpected() {
        return "KubernetesCloud";
    }

    abstract static class EnvVarStrategy {
        void verify(TemplateEnvVar envVar) {
            _verify(envVar);
            assertEquals("key", envVar.getKey());
        }

        abstract void _verify(TemplateEnvVar envVar);
    }

    static class KeyValueEnvVarStrategy extends EnvVarStrategy {

        @Override
        void _verify(TemplateEnvVar envVar) {
            assertThat(envVar, instanceOf(KeyValueEnvVar.class));
            KeyValueEnvVar d = (KeyValueEnvVar) envVar;
            assertEquals("value", d.getValue());
        }
    }

    static class SecretEnvVarStrategy extends EnvVarStrategy {

        @Override
        void _verify(TemplateEnvVar envVar) {
            assertThat(envVar, instanceOf(SecretEnvVar.class));
            SecretEnvVar d = (SecretEnvVar) envVar;
            assertEquals("secretKey", d.getSecretKey());
            assertEquals("secretName", d.getSecretName());
        }
    }
}
