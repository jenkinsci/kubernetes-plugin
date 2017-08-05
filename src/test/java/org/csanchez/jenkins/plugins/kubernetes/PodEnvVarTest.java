package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Test;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class PodEnvVarTest {
    @Test
    public void shouldTransformPodEnvVarToContainerEnvVar() {
        PodEnvVar podEnvVar = new PodEnvVar("key", "value");

        List<AbstractContainerEnvVar> abstractContainerEnvVars = PodEnvVar.asContainerEnvVar(singletonList(podEnvVar));

        assertThat(abstractContainerEnvVars, hasSize(1));
        assertThat(abstractContainerEnvVars, contains(new ContainerEnvVar("key", "value")));
    }

    @Test
    public void shouldNotTransformOtherTypesOfEnvVar() {
        PodSecretEnvVar podSecretEnvVar = new PodSecretEnvVar("key", "secretName", "secretKey");

        List<AbstractContainerEnvVar> abstractContainerEnvVars = PodEnvVar.asContainerEnvVar(singletonList(podSecretEnvVar));

        assertThat(abstractContainerEnvVars, hasSize(0));
    }

}