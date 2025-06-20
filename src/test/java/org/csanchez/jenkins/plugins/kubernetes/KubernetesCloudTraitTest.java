package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import java.util.List;
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
        assertEquals(2, KubernetesCloudTrait.all().size());
    }

    @Test
    public void testKubernetesCloudDescriptorAllTraits() {
        var descriptor = ExtensionList.lookupSingleton(KubernetesCloud.DescriptorImpl.class);
        assertEquals(2, descriptor.getAllTraits().size());
    }

    @Test
    public void testKubernetesCloudDescriptorDefaultTraits() {
        var descriptor = ExtensionList.lookupSingleton(KubernetesCloud.DescriptorImpl.class);
        var traits = descriptor.getDefaultTraits();
        assertEquals(1, traits.size());
        var traitB = traits.get(TraitB.class);
        assertThat(traitB, notNullValue());
        assertThat(traitB.getValue(), equalTo("default"));
    }

    @WithoutJenkins
    @Test
    public void testKubernetesCloudTraits() {
        var cloud = new KubernetesCloud("Foo");

        // empty optional when trait instance not found
        assertTrue(cloud.getTrait(TraitA.class).isEmpty());

        // set traits
        var traitA = new TraitA();
        cloud.setTraits(List.of(traitA, new TraitB("foo")));
        assertEquals(2, cloud.getTraits().size());

        // get trait by class
        var maybeTraitA = cloud.getTrait(TraitA.class);
        assertTrue(maybeTraitA.isPresent());
        assertSame(traitA, maybeTraitA.get());

        // handle null values
        cloud.setTraits(null);
        assertTrue(cloud.getTraits().isEmpty());
    }

    public static class TraitA extends KubernetesCloudTrait {
        @DataBoundConstructor
        public TraitA() {}

        @TestExtension
        public static class DescriptorImpl extends KubernetesCloudTraitDescriptor {
            @NonNull
            @Override
            public String getDisplayName() {
                return "Trait A";
            }
        }
    }

    public static class TraitB extends KubernetesCloudTrait {

        private final String value;

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
