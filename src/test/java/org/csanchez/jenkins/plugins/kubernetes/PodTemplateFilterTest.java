package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Label;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class PodTemplateFilterTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @TestExtension
    public static class PodTemplateFilter1 extends PodTemplateFilter {
        @CheckForNull
        @Override
        protected PodTemplate transform(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate, @CheckForNull Label label) {
            return addYaml(podTemplate, "yaml1");
        }
    }

    @TestExtension
    public static class PodTemplateFilter2 extends PodTemplateFilter {
        @CheckForNull
        @Override
        protected PodTemplate transform(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate, @CheckForNull Label label) {
            return addYaml(podTemplate, "yaml2");
        }
    }

    @Test
    public void multipleFilters() {
        List<PodTemplate> podtemplates = new ArrayList<>();
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel("label");
        podtemplates.add(podTemplate);
        List<PodTemplate> result = PodTemplateFilter.applyAll(null, podtemplates, Label.get("label"));
        assertEquals(1, result.size());
        assertThat(result.get(0).getYamls(), Matchers.containsInAnyOrder("yaml1", "yaml2"));
    }

    private static PodTemplate addYaml(@NonNull PodTemplate podTemplate, String yaml) {
        PodTemplate result = new PodTemplate(podTemplate);
        List<String> yamls = new ArrayList<>(result.getYamls());
        yamls.add(yaml);
        result.setYamls(yamls);
        return result;
    }
}
