package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import org.csanchez.jenkins.plugins.kubernetes.pod.decorator.PodDecorator;
import org.junit.Before;
import org.junit.Test;

public class RestrictedPssSecurityInjectorTest extends AbstractGoldenFileTest {
    @Before
    public void configureCloud() {
        cloud.setRestrictedPssSecurityContext(true);
    }

    @Override
    protected PodDecorator newDecorator() {
        return new RestrictedPssSecurityContextInjector();
    }

    @Test
    public void simple() throws IOException {
        test("simple");
    }

    @Test
    public void multiContainer() throws IOException {
        test("multiContainer");
    }

    @Test
    public void existingSecurityContext() throws IOException {
        test("existingSecurityContext");
    }
}
