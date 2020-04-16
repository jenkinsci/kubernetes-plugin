package org.csanchez.jenkins.plugins.kubernetes.casc;

import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(Parameterized.class)
public class EnvVarCasCTest extends RoundTripAbstractTest {
    final EnvVarStrategy strategy;
    final String resource;

    public EnvVarCasCTest(EnvVarStrategy strategy, String resource) {
        this.strategy = strategy;
        this.resource = resource;
    }

    @Parameterized.Parameters(name = "{index}: {1}")
    public static Object[] permutations() {
        return new Object[][] {
                {new KeyValueEnvVarStrategy(), "keyValue"},
                {new SecretEnvVarStrategy(), "secret"},
        };

    }

    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule r, String configContent) {
        KubernetesCloud cloud = r.j.jenkins.clouds.get(KubernetesCloud.class);
        assertNotNull(cloud);
        assertEquals("10",cloud.getContainerCapStr());
        assertEquals("http://jenkinshost:8080/jenkins/", cloud.getJenkinsUrl());
        assertEquals("32", cloud.getMaxRequestsPerHostStr());
        assertEquals("kubernetes", cloud.name);
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
            assertEquals("key",envVar.getKey());
        }
        abstract void _verify(TemplateEnvVar envVar);
    }

    static class KeyValueEnvVarStrategy extends EnvVarStrategy {

        @Override
        void _verify(TemplateEnvVar envVar) {
            assertThat(envVar, instanceOf(KeyValueEnvVar.class));
            KeyValueEnvVar d = (KeyValueEnvVar) envVar;
            assertEquals("value",d.getValue());
        }
    }

    static class SecretEnvVarStrategy extends EnvVarStrategy {

        @Override
        void _verify(TemplateEnvVar envVar) {
            assertThat(envVar, instanceOf(SecretEnvVar.class));
            SecretEnvVar d = (SecretEnvVar) envVar;
            assertEquals("secretKey",d.getSecretKey());
            assertEquals("secretName",d.getSecretName());
        }
    }

}
