package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

public class PodTemplateTest {
    @Test
    public void getYamlsExposesSingleYamlField() {
        PodTemplate podTemplate = new PodTemplate();
        assertThat(podTemplate.getYamls(), empty());
        podTemplate.setYamls(null);
        assertThat(podTemplate.getYamls(), empty());
        podTemplate.setYaml("yaml");
        assertThat(podTemplate.getYamls(), contains("yaml"));
        podTemplate.setYaml(null);
        assertThat(podTemplate.getYamls(), empty());
    }
}
