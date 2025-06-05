package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Descriptor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
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

    @TestExtension
    public static class TraitA extends KubernetesCloudTrait {
        @DataBoundConstructor
        public TraitA() {}

        @TestExtension
        public static class TraitADescriptor extends Descriptor<KubernetesCloudTrait> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Trait A";
            }
        }
    }

    @TestExtension
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
