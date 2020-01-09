package org.csanchez.jenkins.plugins.kubernetes;

import hudson.util.XStream2;
import org.junit.Test;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplate.*;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.*;

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

    @Test
    public void copyConstructor() throws Exception {
        XStream2 xs = new XStream2();
        PodTemplate pt = new PodTemplate();
        assertEquals(xs.toXML(pt), xs.toXML(new PodTemplate(pt)));
        pt.setActiveDeadlineSeconds(99);
        assertEquals(xs.toXML(pt), xs.toXML(new PodTemplate(pt)));
        pt.setIdleMinutes(99);
        assertEquals(xs.toXML(pt), xs.toXML(new PodTemplate(pt)));
    }

    @Test
    public void sanitizeLabels() {
        assertEquals("label1", sanitizeLabel("label1"));
        assertEquals("label1_label2", sanitizeLabel("label1 label2"));
        assertEquals("el1_label2_verylooooooooooooooooooooooooooooonglabelover63chars", sanitizeLabel("label1 label2 verylooooooooooooooooooooooooooooonglabelover63chars"));
        assertEquals("xfoo_bar", sanitizeLabel(":foo:bar"));
    }

}
