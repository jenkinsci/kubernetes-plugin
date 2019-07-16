package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PodTemplateTest {

    @Mock
    KubernetesSlave slave;

    @Mock
    KubernetesCloud cloud;

    @Before
    public void setUp() {
        when(slave.getPodName()).thenReturn("pod-name");
        when(slave.getKubernetesCloud()).thenReturn(cloud);
        Map<String, String> labels = new HashMap<>();
        labels.put("foo", "bar");
        when(cloud.getLabels()).thenReturn(labels);
        when(slave.getComputer()).thenReturn(null);
        when(slave.getNodeName()).thenReturn("node-name");
        when(cloud.isAddMasterProxyEnvVars()).thenReturn(false);
        when(cloud.isOpenShift()).thenReturn(false);
    }

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
        String description = pt.getDescriptionForLogging(slave);
        assertThat(description, containsString("name: \"foo\""));
        assertThat(description, containsString("image: \"image\""));
        assertThat(description, containsString("foo: \"bar\"\n"));

        pt.setShowRawYaml(false);
        description = pt.getDescriptionForLogging(slave);
        assertThat(description, not(containsString("foo: \"bar\"\n")));
    }
}
