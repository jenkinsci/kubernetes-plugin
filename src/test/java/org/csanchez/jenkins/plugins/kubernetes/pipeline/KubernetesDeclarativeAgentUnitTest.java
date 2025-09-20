package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isA;

import java.util.Collections;
import java.util.Map;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Never;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
import org.jenkinsci.plugins.pipeline.modeldefinition.generator.AgentDirective;
import org.jenkinsci.plugins.pipeline.modeldefinition.generator.DirectiveGeneratorTester;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class KubernetesDeclarativeAgentUnitTest {

    private static JenkinsRule j;

    private KubernetesDeclarativeAgent instance;

    private DirectiveGeneratorTester dg;
    private AgentDirective directive;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        j = rule;
    }

    @BeforeEach
    void beforeEach() {
        instance = new KubernetesDeclarativeAgent();
        directive = new AgentDirective(instance);
        dg = new DirectiveGeneratorTester(j);
    }

    @Test
    void serializationNull() {
        Map<String, Object> args = instance.getAsArgs();
        assertThat(args, equalTo(Collections.emptyMap()));
    }

    @Test
    void serialization() {
        instance.setCloud("cloud");
        instance.setLabel("label");
        instance.setYaml("yaml");
        instance.setYamlMergeStrategy(new Merge());
        DynamicPVCWorkspaceVolume workspaceVolume = new DynamicPVCWorkspaceVolume();
        workspaceVolume.setStorageClassName("sc");
        workspaceVolume.setRequestsSize("1G");
        workspaceVolume.setAccessModes("ReadWrite");
        instance.setWorkspaceVolume(workspaceVolume);
        instance.setIdleMinutes(1);
        instance.setInheritFrom("inheritFrom");
        instance.setAgentContainer("agentContainer");
        instance.setAgentInjection(true);
        Map<String, Object> args = instance.getAsArgs();

        assertThat(args.get("cloud"), equalTo("cloud"));
        assertThat(args.get("label"), equalTo("label"));
        assertThat(args.get("yaml"), equalTo("yaml"));
        assertThat(args.get("yamlMergeStrategy"), isA(Merge.class));
        assertThat(args.get("workspaceVolume"), equalTo(workspaceVolume));
        assertThat(args.get("idleMinutes"), equalTo(1));
        assertThat(args.get("inheritFrom"), equalTo("inheritFrom"));
        assertThat(args.get("agentContainer"), equalTo("agentContainer"));
        assertThat(args.get("agentInjection"), equalTo(true));
    }

    @Test
    void simpleGenerator() throws Exception {
        dg.assertGenerateDirective(
                directive, """
                agent {
                  kubernetes true
                }""");
    }

    @Test
    void complexGenerator() throws Exception {
        instance.setCloud("cloud");
        instance.setYaml("yaml");
        instance.setYamlMergeStrategy(new Merge());
        DynamicPVCWorkspaceVolume workspaceVolume = new DynamicPVCWorkspaceVolume();
        workspaceVolume.setStorageClassName("sc");
        workspaceVolume.setRequestsSize("1G");
        workspaceVolume.setAccessModes("ReadWrite");
        instance.setWorkspaceVolume(workspaceVolume);
        instance.setPodRetention(new Never());
        instance.setInheritFrom("inheritFrom");
        dg.assertGenerateDirective(
                directive,
                """
                        agent {
                          kubernetes {
                            cloud 'cloud'
                            inheritFrom 'inheritFrom'
                            podRetention never()
                            workspaceVolume dynamicPVC(accessModes: 'ReadWrite', requestsSize: '1G', storageClassName: 'sc')
                            yaml 'yaml'
                            yamlMergeStrategy merge()
                          }
                        }""");
    }
}
