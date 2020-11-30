package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.mockito.Mockito;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;

public class PodDecoratorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @TestExtension("activeDecorator")
    public static class PodDecoratorImpl implements PodDecorator {
        @Nonnull
        @Override
        public Pod decorate(@Nonnull KubernetesCloud kubernetesCloud, @Nonnull Pod pod) {
            // @formatter:off
            return new PodBuilder(pod)
                    .editOrNewMetadata()
                        .addToLabels("poddecoratorimpl","true")
                    .endMetadata()
                .build();
            // @formatter:on
        }
    }

    @Test
    public void activeDecorator() {
        PodTemplate podTemplate = new PodTemplate();
        PodTemplateBuilder podTemplateBuilder = new PodTemplateBuilder(podTemplate, Mockito.mock(KubernetesSlave.class)).withCloud(new KubernetesCloud("kubernetes"));
        Pod pod = podTemplateBuilder.build();
        assertEquals("true", pod.getMetadata().getLabels().get("poddecoratorimpl"));
    }
}
