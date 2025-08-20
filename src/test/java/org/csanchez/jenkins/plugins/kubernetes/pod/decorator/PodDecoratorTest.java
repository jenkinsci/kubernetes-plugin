package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@WithJenkins
@ExtendWith(MockitoExtension.class)
class PodDecoratorTest {

    private JenkinsRule j;

    @Mock
    private KubernetesSlave slave;

    private KubernetesCloud cloud = new KubernetesCloud("test");

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
        when(slave.getKubernetesCloud()).thenReturn(cloud);
    }

    @TestExtension("activeDecorator")
    public static class PodDecoratorImpl implements PodDecorator {
        @NonNull
        @Override
        public Pod decorate(@NonNull KubernetesCloud kubernetesCloud, @NonNull Pod pod) {
            // @formatter:off
            return new PodBuilder(pod)
                    .editOrNewMetadata()
                    .addToLabels("poddecoratorimpl", "true")
                    .endMetadata()
                    .build();
            // @formatter:on
        }
    }

    @Test
    void activeDecorator() {
        PodTemplate podTemplate = new PodTemplate();
        PodTemplateBuilder podTemplateBuilder = new PodTemplateBuilder(podTemplate, slave);
        Pod pod = podTemplateBuilder.build();
        assertEquals("true", pod.getMetadata().getLabels().get("poddecoratorimpl"));
    }
}
