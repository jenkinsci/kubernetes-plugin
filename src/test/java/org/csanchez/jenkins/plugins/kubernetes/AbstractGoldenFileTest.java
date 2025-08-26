package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.pod.decorator.PodDecorator;
import org.junit.jupiter.api.BeforeEach;

abstract class AbstractGoldenFileTest {

    protected KubernetesCloud cloud;
    protected PodDecorator decorator;

    @BeforeEach
    void beforeEach() {
        decorator = newDecorator();
        cloud = new KubernetesCloud("test");
    }

    protected abstract PodDecorator newDecorator();

    protected void test(String name) throws Exception {
        var beforeYAML = loadFileAsStream(name + "-before.yaml");
        var before = Serialization.unmarshal(beforeYAML, Pod.class);
        assertEquals(Serialization.asYaml(before), beforeYAML, name + "-before.yaml is not normalized");
        var afterYAML = loadFileAsStream(name + "-after.yaml");
        var after = decorator.decorate(cloud, before);
        assertEquals(Serialization.asYaml(after), afterYAML, name + "-after.yaml processed");
    }

    @NonNull
    private String loadFileAsStream(String name) throws Exception {
        var is = getClass().getResourceAsStream(getClass().getSimpleName() + "/" + name);
        if (is == null) {
            throw new IllegalStateException("Test file \"src/test/resources/"
                    + getClass().getPackageName().replace(".", "/") + "/"
                    + getClass().getSimpleName() + "/" + name + "\" not found");
        }
        return IOUtils.toString(is, StandardCharsets.UTF_8);
    }
}
