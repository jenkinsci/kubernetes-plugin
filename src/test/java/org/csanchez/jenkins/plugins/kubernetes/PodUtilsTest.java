package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang.StringUtils;
import org.junit.Test;

public class PodUtilsTest {

    @Test
    public void generateRandomSuffix() {
        List<String> generated = IntStream.range(0, 100)
                .mapToObj(i -> PodUtils.generateRandomSuffix())
                .collect(Collectors.toList());
        Set<String> unique = new HashSet<>(generated);
        assertEquals(generated.size(), unique.size());

        for (String suffix : generated) {
            assertEquals(5, suffix.length());
            assertTrue(PodUtils.isValidName(suffix));
        }
    }

    @Test
    public void createNameWithRandomSuffix() {
        String name = PodUtils.createNameWithRandomSuffix("foo");
        assertEquals(9, name.length());
        assertTrue(name.startsWith("foo-"));
        assertTrue(PodUtils.isValidName(name));

        // names with invalid characters
        name = PodUtils.createNameWithRandomSuffix("foo bar_cat");
        assertEquals(17, name.length());
        assertTrue(name.startsWith("foo-bar-cat-"));

        // very long names
        name = PodUtils.createNameWithRandomSuffix(StringUtils.repeat("a", 70));
        assertEquals(63, name.length());
        assertTrue(name.startsWith(StringUtils.repeat("a", 57) + "-"));
    }

    @Test
    public void isValidName() {
        assertTrue(PodUtils.isValidName("foo"));
        assertTrue(PodUtils.isValidName("foo-bar"));
        assertTrue(PodUtils.isValidName("foo.bar"));
        assertTrue(PodUtils.isValidName("foo.123"));
        assertTrue(PodUtils.isValidName("123-foo"));
        assertFalse(PodUtils.isValidName("foo bar"));
        assertFalse(PodUtils.isValidName("-foo"));
        assertFalse(PodUtils.isValidName(".foo"));
    }

    @Test
    public void getContainerStatusEphemeralContainer() {
        Pod pod = new PodBuilder()
                .withNewStatus()
                .addNewContainerStatus()
                .withName("foo")
                .endContainerStatus()
                .addNewEphemeralContainerStatus()
                .withName("bar")
                .endEphemeralContainerStatus()
                .endStatus()
                .build();
        Optional<ContainerStatus> status = PodUtils.getContainerStatus(pod, "bar");
        assertTrue(status.isPresent());
        assertEquals("bar", status.get().getName());
    }

    @Test
    public void getContainerWorkingDir() {
        Pod pod = new PodBuilder()
                .withNewSpec()
                .addNewContainer()
                .withName("foo")
                .withWorkingDir("/app/foo")
                .endContainer()
                .addNewEphemeralContainer()
                .withName("bar")
                .withWorkingDir("/app/bar")
                .endEphemeralContainer()
                .endSpec()
                .build();

        assertFalse(PodUtils.getContainerWorkingDir(null, "foo").isPresent());

        Optional<String> wd = PodUtils.getContainerWorkingDir(pod, "foo");
        assertTrue(wd.isPresent());
        assertEquals("/app/foo", wd.get());

        wd = PodUtils.getContainerWorkingDir(pod, "bar");
        assertTrue(wd.isPresent());
        assertEquals("/app/bar", wd.get());

        wd = PodUtils.getContainerWorkingDir(pod, "fish");
        assertFalse(wd.isPresent());
    }
}
