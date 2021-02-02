package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;

public class KubernetesProvisioningLimitsTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule log = new LoggerRule().record(KubernetesProvisioningLimits.class, Level.FINEST);

    @Test
    public void lotsOfCloudsAndTemplates() throws InterruptedException {
        ThreadLocalRandom testRandom = ThreadLocalRandom.current();
        for (int i = 1; i <= 10; i++) {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes-" + i);
            cloud.setContainerCap(testRandom.nextInt(40)+1);
            for (int j = 1; j < 10; j++) {
                PodTemplate pt = new PodTemplate();
                pt.setInstanceCap(testRandom.nextInt(4)+1);
                cloud.addTemplate(pt);
            }
            j.jenkins.clouds.add(cloud);
        }
        ExecutorService EXEC = Executors.newFixedThreadPool(100);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int k = 0 ; k < 1000; k++) {
            Callable<Void> c = () -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                List<KubernetesCloud> clouds = j.jenkins.clouds.getAll(KubernetesCloud.class);
                KubernetesCloud cloud = clouds.get(random.nextInt(clouds.size()));
                List<PodTemplate> templates = cloud.getTemplates();
                PodTemplate podTemplate = templates.get(random.nextInt(templates.size()));
                if (KubernetesProvisioningLimits.get().register(cloud, podTemplate, 1)) {
                    Thread.sleep(random.nextInt(3) * 1000);
                    KubernetesProvisioningLimits.get().unregister(cloud, podTemplate, 1);
                }
                return null;
            };
            tasks.add(c);
        }
        EXEC.invokeAll(tasks);

        // Check that every count is back to 0
        for (KubernetesCloud cloud : j.jenkins.clouds.getAll(KubernetesCloud.class)) {
            assertEquals(0, KubernetesProvisioningLimits.get().getGlobalCount(cloud.name).get());
            for (PodTemplate template : cloud.getTemplates()) {
                assertEquals(0, KubernetesProvisioningLimits.get().getPodTemplateCount(template.getId()).get());
            }
        }
    }
}
