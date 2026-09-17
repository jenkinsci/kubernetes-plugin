package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assertRegex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.slaves.NodeProvisioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class StandardPlannedNodeBuilderTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void testBuild() {
        KubernetesCloud cloud = new KubernetesCloud("Cloud");
        PodTemplate template = new PodTemplate("t");
        template.setName("Template");
        r.jenkins.clouds.add(cloud);

        NodeProvisioner.PlannedNode plannedNode = new StandardPlannedNodeBuilder()
                .cloud(cloud)
                .template(template)
                .numExecutors(1)
                .build();

        // The default builder returns a plain PlannedNode carrying no cloud-stats tracking; tracking is
        // layered on only by the optional integration package (JENKINS-67256).
        assertEquals(NodeProvisioner.PlannedNode.class, plannedNode.getClass());
        assertNotNull(plannedNode.displayName, "a planned node should carry a display name");
        assertRegex(plannedNode.displayName, "^template-[0-9a-z]{5}$");
    }

    @Test
    void testExtensionSeamUsesDownstreamFactory() {
        // The default builder must not monopolise the PlannedNodeBuilder extension point: a downstream
        // plugin's factory still wins over StandardPlannedNodeBuilder.
        PlannedNodeBuilder builder = PlannedNodeBuilderFactory.createInstance();
        assertTrue(
                builder instanceof FixedPlannedNodeBuilder,
                "a registered downstream factory should be preferred over the default builder");
    }

    @TestExtension("testExtensionSeamUsesDownstreamFactory")
    public static class FixedPlannedNodeBuilderFactory extends PlannedNodeBuilderFactory {
        @Override
        public PlannedNodeBuilder newInstance() {
            return new FixedPlannedNodeBuilder();
        }
    }

    /**
     * A stand-in downstream builder. build() delegates to the standard builder so that, even if the
     * scoped {@link TestExtension} ever leaked into another test, provisioning would still work.
     */
    public static final class FixedPlannedNodeBuilder extends PlannedNodeBuilder {
        @Override
        public NodeProvisioner.PlannedNode build() {
            return new StandardPlannedNodeBuilder()
                    .cloud(getCloud())
                    .template(getTemplate())
                    .label(getLabel())
                    .numExecutors(getNumExecutors())
                    .build();
        }
    }
}
