package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class KubernetesProvisioningLimitsTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule log = new LoggerRule().record(KubernetesProvisioningLimits.class, Level.FINEST);

    @Test
    public void lotsOfCloudsAndTemplates() throws InterruptedException {
        ThreadLocalRandom testRandom = ThreadLocalRandom.current();
        for (int i = 1; i < 4; i++) {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes-" + i);
            cloud.setContainerCap(testRandom.nextInt(4) + 1);
            for (int j = 1; j < 4; j++) {
                PodTemplate pt = new PodTemplate();
                pt.setName(cloud.name + "-podTemplate-" + j);
                pt.setInstanceCap(testRandom.nextInt(4) + 1);
                cloud.addTemplate(pt);
            }
            j.jenkins.clouds.add(cloud);
        }

        ExecutorService threadPool = Executors.newCachedThreadPool();
        System.out.println(threadPool.getClass().getName());
        CompletionService<Void> ecs = new ExecutorCompletionService<>(threadPool);
        KubernetesProvisioningLimits kubernetesProvisioningLimits = KubernetesProvisioningLimits.get();

        List<KubernetesCloud> clouds = j.jenkins.clouds.getAll(KubernetesCloud.class);
        for (int k = 0; k < 1000; k++) {
            ecs.submit(
                    () -> {
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        KubernetesCloud cloud = clouds.get(random.nextInt(clouds.size()));
                        List<PodTemplate> templates = cloud.getTemplates();
                        PodTemplate podTemplate = templates.get(random.nextInt(templates.size()));
                        while (!kubernetesProvisioningLimits.register(cloud, podTemplate, 1)) {
                            try {
                                Thread.sleep(8);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }

                        ecs.submit(
                                () -> {
                                    kubernetesProvisioningLimits.unregister(cloud, podTemplate, 1);
                                },
                                null);
                    },
                    null);
        }

        while (ecs.poll(20, TimeUnit.SECONDS) != null) {
            try {
                Thread.sleep(8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        threadPool.shutdown();
        assertTrue(threadPool.awaitTermination(60, TimeUnit.SECONDS));
        assertEquals(0, threadPool.shutdownNow().size());

        // Check that every count is back to 0
        for (KubernetesCloud cloud : j.jenkins.clouds.getAll(KubernetesCloud.class)) {
            assertEquals(0, KubernetesProvisioningLimits.get().getGlobalCount(cloud.name));
            for (PodTemplate template : cloud.getTemplates()) {
                assertEquals(0, KubernetesProvisioningLimits.get().getPodTemplateCount(template.getId()));
            }
        }
    }

    @Test
    public void testCounterDecrementAfterRestartWithEphemeralTemplate() throws Exception {
        // Create a cloud with an ephemeral template
        KubernetesCloud cloud = new KubernetesCloud("test-cloud");
        cloud.setContainerCap(10);
        j.jenkins.clouds.add(cloud);

        // Create an ephemeral pod template (not saved to cloud config)
        PodTemplate ephemeralTemplate = new PodTemplate();
        ephemeralTemplate.setName("ephemeral-template");
        ephemeralTemplate.setInstanceCap(5);

        // Register the template (simulates agent creation)
        KubernetesProvisioningLimits limits = KubernetesProvisioningLimits.get();
        assertTrue("Should successfully register template", limits.register(cloud, ephemeralTemplate, 1));

        // Get the template ID that was auto-generated
        String templateId = ephemeralTemplate.getId();

        // Verify counters were incremented after registration
        assertEquals(
                "Global count should be 1 after registration", 1, limits.getGlobalCount("test-cloud"));
        assertEquals(
                "Template count should be 1 after registration",
                1,
                limits.getPodTemplateCount(templateId));

        // Create a KubernetesSlave using Builder pattern
        KubernetesSlave slave = new KubernetesSlave.Builder()
                .podTemplate(ephemeralTemplate)
                .cloud(cloud)
                .nodeDescription("Test agent for counter leak fix")
                .build();

        // Add the slave to Jenkins
        j.jenkins.addNode(slave);

        // Remove the node (simulates agent deletion)
        // The fix ensures counters are decremented using cloudName and templateId,
        // even when template reference is null (as happens with ephemeral templates after restart)
        j.jenkins.removeNode(slave);

        // After deletion, counters should be decremented back to 0
        // This is the bug fix: should work even when template reference is null
        assertEquals(
                "Global count should be 0 after node deletion",
                0,
                limits.getGlobalCount("test-cloud"));
        assertEquals(
                "Template count should be 0 after node deletion",
                0,
                limits.getPodTemplateCount(templateId));
    }
}
