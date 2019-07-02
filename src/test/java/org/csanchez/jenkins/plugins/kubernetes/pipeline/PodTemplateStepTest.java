package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PodTemplateStepTest {

    @Test
    public void honorsShowRawYaml() {
        PodTemplateStep pt = new PodTemplateStep("myLabel", "myName");

        pt.setShowRawYaml(false);
        assertThat(pt.isShowRawYaml(), is(false));
    }
}
