package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import static org.csanchez.jenkins.plugins.kubernetes.pod.decorator.DefaultSafeToEvict.SAFE_TO_EVICT_ANNOTATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.mockito.Mockito.mock;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.junit.jupiter.api.Test;

class DefaultSafeToEvictTest {

    private final DefaultSafeToEvict decorator = new DefaultSafeToEvict();
    private final KubernetesCloud cloud = mock(KubernetesCloud.class);

    @Test
    void setsAnnotationWhenAbsent() {
        Pod pod = new PodBuilder().withNewMetadata().endMetadata().build();
        Pod decorated = decorator.decorate(cloud, pod);
        assertThat(decorated.getMetadata().getAnnotations(), hasEntry(SAFE_TO_EVICT_ANNOTATION, "false"));
    }

    @Test
    void preservesExistingAnnotations() {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .addToAnnotations("existing", "value")
                .endMetadata()
                .build();
        Pod decorated = decorator.decorate(cloud, pod);
        assertThat(decorated.getMetadata().getAnnotations(), hasEntry("existing", "value"));
        assertThat(decorated.getMetadata().getAnnotations(), hasEntry(SAFE_TO_EVICT_ANNOTATION, "false"));
    }

    @Test
    void doesNotOverrideUserValue() {
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .addToAnnotations(SAFE_TO_EVICT_ANNOTATION, "true")
                .endMetadata()
                .build();
        Pod decorated = decorator.decorate(cloud, pod);
        assertThat(decorated.getMetadata().getAnnotations(), hasEntry(SAFE_TO_EVICT_ANNOTATION, "true"));
    }
}
