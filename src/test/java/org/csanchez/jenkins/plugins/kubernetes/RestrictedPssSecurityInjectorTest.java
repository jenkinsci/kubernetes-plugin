package org.csanchez.jenkins.plugins.kubernetes;

import org.csanchez.jenkins.plugins.kubernetes.pod.decorator.PodDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RestrictedPssSecurityInjectorTest extends AbstractGoldenFileTest {

    @BeforeEach
    void configureCloud() {
        cloud.setRestrictedPssSecurityContext(true);
    }

    @Override
    protected PodDecorator newDecorator() {
        return new RestrictedPssSecurityContextInjector();
    }

    @Test
    void simple() throws Exception {
        test("simple");
    }

    @Test
    void multiContainer() throws Exception {
        test("multiContainer");
    }

    @Test
    void existingSecurityContext() throws Exception {
        test("existingSecurityContext");
    }

    @Test
    void agentInjection() throws Exception {
        test("agentInjection");
    }
}
