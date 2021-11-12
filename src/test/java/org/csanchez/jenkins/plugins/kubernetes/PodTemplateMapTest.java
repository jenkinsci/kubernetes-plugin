package org.csanchez.jenkins.plugins.kubernetes;

import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.PodTemplateMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class PodTemplateMapTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private PodTemplateMap instance;
    private KubernetesCloud cloud;

    @Before
    public void setUp() throws IOException {
        this.instance = PodTemplateMap.get();
        this.cloud = new KubernetesCloud("kubernetes");
        j.jenkins.clouds.add(cloud);
        j.jenkins.save();
    }

    @Test
    public void concurrentAdds() throws Exception {

        assertEquals(0, this.instance.getTemplates(cloud).size());
        int n = 10;
        Thread[] t = new Thread[n];
        for (int i = 0; i < t.length; i++) {
            t[i] = newThread(i);
        }
        for (Thread thread : t) {
            thread.start();
        }
        for (Thread thread : t) {
            thread.join();
        }
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> this.instance.getTemplates(cloud).size() == n);
    }

    private Thread newThread(int i) {
        String name = "test-" + i;
        return new Thread(() -> {
            instance.addTemplate(cloud, buildPodTemplate(name));
        }, name);
    }

    private PodTemplate buildPodTemplate(String label) {
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(label);
        return podTemplate;
    }
}
