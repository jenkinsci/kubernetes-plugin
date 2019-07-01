package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

public class PodTemplateTest {

    @Test
    public void descriptionHonorsShowRawYaml() {
        PodTemplate pt = new PodTemplate();
        ContainerTemplate ct1 = new ContainerTemplate("foo", "image");
        pt.setContainers(Collections.singletonList(ct1));
        String yaml =
                "metadata:\n" +
                "  annotations:\n" +
                "    foo: bar\n";
        pt.setYamls(Collections.singletonList(yaml));
        String description = pt.getDescriptionForLogging();
        assertThat(description, containsString("[foo] image"));
        assertThat(description, containsString("yaml:\n"+yaml));

        pt.setShowRawYaml(false);
        description = pt.getDescriptionForLogging();
        assertThat(description, not(containsString("yaml:\n")));
        assertThat(description, not(containsString(yaml)));
    }
}
