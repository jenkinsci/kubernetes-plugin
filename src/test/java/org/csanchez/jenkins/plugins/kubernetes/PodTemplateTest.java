package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class PodTemplateTest {
    @Test
    public void getYamlsExposesSingleYamlField() {
        PodTemplate podTemplate = new PodTemplate();
        assertThat(podTemplate.getYamls(), empty());
        podTemplate.setYamls(null);
        assertThat(podTemplate.getYamls(), empty());
        podTemplate.setYaml("yaml");
        List<String> yamls = podTemplate.getYamls();
        assertThat(yamls, hasSize(1));
        assertEquals("yaml", yamls.get(0));
        podTemplate.setYaml(null);
        assertThat(podTemplate.getYamls(), empty());
    }
}
