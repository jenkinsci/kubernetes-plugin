package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.Merge;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
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

    @Before
    public void setUp() {
        instance = new KubernetesDeclarativeAgent();
    }

    @Test
    public void serializationNull() {
        Map<String, Object> args = instance.getAsArgs();
        assertThat(args, equalTo(Collections.emptyMap()));
    }

    @Test
    public void serialization() {
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
}
