package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.WithoutJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

public class KubernetesCloudTraitTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testKubernetesCloudAll() {
        List<? extends Descriptor<KubernetesCloudTrait>> traits = KubernetesCloudTrait.all();
        assertEquals(2, traits.size());
    }

    @Test
    public void testKubernetesCloudDescriptorAllTraits() {
        KubernetesCloud.DescriptorImpl descriptor = new KubernetesCloud.DescriptorImpl();
        List<? extends Descriptor<KubernetesCloudTrait>> traits = descriptor.getAllTraits();
        assertEquals(2, traits.size());
    }

    @Test
    public void testKubernetesCloudDescriptorDefaultTraits() {
        KubernetesCloud.DescriptorImpl descriptor = new KubernetesCloud.DescriptorImpl();
        Map<Descriptor<KubernetesCloudTrait>, KubernetesCloudTrait> traits = descriptor.getDefaultTraits();
        assertEquals(1, traits.size());
        Map.Entry<Descriptor<KubernetesCloudTrait>, KubernetesCloudTrait> entry =
                traits.entrySet().stream().findFirst().orElseThrow();
        assertTrue(entry.getKey() instanceof TraitB.DescriptorImpl);
        assertTrue(entry.getValue() instanceof TraitB);
        assertEquals("default", ((TraitB) entry.getValue()).getValue());
    }

    @WithoutJenkins
    @Test
    public void testKubernetesCloudTraits() {
        KubernetesCloud cloud = new KubernetesCloud("Foo");

        // empty optional when trait instance not found
        assertTrue(cloud.getTrait(TraitA.class).isEmpty());

        // set traits
        TraitA a = new TraitA();
        cloud.setTraits(List.of(a, new TraitB()));
        assertEquals(2, cloud.getTraits().size());

        // get trait by class
        Optional<TraitA> ta = cloud.getTrait(TraitA.class);
        assertTrue(ta.isPresent());
        assertSame(a, ta.get());

        // handle null values
        cloud.setTraits(null);
        assertTrue(cloud.getTraits().isEmpty());
    }

    public static class TraitA extends KubernetesCloudTrait {
        @DataBoundConstructor
        public TraitA() {}

        @TestExtension
        public static class DescriptorImpl extends Descriptor<KubernetesCloudTrait> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Trait A";
            }
        }
    }

    public static class TraitB extends KubernetesCloudTrait {

        private final String value;

        public TraitB() {
            this("constructor");
        }

        @DataBoundConstructor
        public TraitB(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @TestExtension
        public static class DescriptorImpl extends KubernetesCloudTraitDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Trait B";
            }

            @Override
            public Optional<KubernetesCloudTrait> getDefaultTrait() {
                return Optional.of(new TraitB("default"));
            }
        }
    }
}
