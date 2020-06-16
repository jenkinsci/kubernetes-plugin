package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
import org.jenkinsci.plugins.pipeline.modeldefinition.generator.AgentDirective;
import org.jenkinsci.plugins.pipeline.modeldefinition.generator.DirectiveGeneratorTester;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;

public class KubernetesDeclarativeAgentUnitTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    KubernetesDeclarativeAgent instance;

    DirectiveGeneratorTester dg;
    AgentDirective directive;

    @Before
    public void setUp() {
        instance = new KubernetesDeclarativeAgent();
        directive = new AgentDirective(instance);
        dg = new DirectiveGeneratorTester(j);
    }

    @Test
    public void serializationNull() {
        Map<String, Object> args = instance.getAsArgs();
        assertThat(args, equalTo(Collections.emptyMap()));
    }

    @Test
    public void serialization() throws Exception {
        instance.setCloud("cloud");
        instance.setLabel("label");
        instance.setYaml("yaml");
        instance.setYamlMergeStrategy(new Merge());
        DynamicPVCWorkspaceVolume workspaceVolume = new DynamicPVCWorkspaceVolume("sc", "1G", "ReadWrite");
        instance.setWorkspaceVolume(workspaceVolume);
        instance.setIdleMinutes(1);
        instance.setInheritFrom("inheritFrom");
        Map<String, Object> args = instance.getAsArgs();

        assertThat(args.get("cloud"), equalTo("cloud"));
        assertThat(args.get("label"), equalTo("label"));
        assertThat(args.get("yaml"), equalTo("yaml"));
        assertThat(args.get("yamlMergeStrategy"),isA(Merge.class));
        assertThat(args.get("workspaceVolume"),equalTo(workspaceVolume));
        assertThat(args.get("idleMinutes"), equalTo(1));
        assertThat(args.get("inheritFrom"), equalTo("inheritFrom"));
    }

    @Test
    public void simpleGenerator() throws Exception {
        dg.assertGenerateDirective(directive, "agent {\n" +
                "  kubernetes true\n" +
                "}");
    }

    @Test
    public void complexGenerator() throws Exception {
        instance.setCloud("cloud");
        instance.setYaml("yaml");
        instance.setYamlMergeStrategy(new Merge());
        DynamicPVCWorkspaceVolume workspaceVolume = new DynamicPVCWorkspaceVolume("sc", "1G", "ReadWrite");
        instance.setWorkspaceVolume(workspaceVolume);
        instance.setPodRetention(new Never());
        instance.setInheritFrom("inheritFrom");
        dg.assertGenerateDirective(directive, "agent {\n" +
                "  kubernetes {\n" +
                "    cloud 'cloud'\n" +
                "    inheritFrom 'inheritFrom'\n" +
                "    podRetention never()\n" +
                "    workspaceVolume dynamicPVC(accessModes: 'ReadWrite', requestsSize: '1G', storageClassName: 'sc')\n" +
                "    yaml 'yaml'\n" +
                "    yamlMergeStrategy merge()\n" +
                "  }\n" +
                "}");
    }
}
