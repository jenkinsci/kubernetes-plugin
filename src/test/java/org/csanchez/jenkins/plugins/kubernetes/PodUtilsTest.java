package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.Test;

class PodUtilsTest {

    @Test
    void generateRandomSuffix() {
        List<String> generated = IntStream.range(0, 100)
                .mapToObj(i -> PodUtils.generateRandomSuffix())
                .toList();
        Set<String> unique = new HashSet<>(generated);
        assertEquals(generated.size(), unique.size());

        for (String suffix : generated) {
            assertEquals(5, suffix.length());
            assertTrue(PodUtils.isValidName(suffix));
        }
    }

    @Test
    void createNameWithRandomSuffix() {
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
    void isValidName() {
        assertTrue(PodUtils.isValidName("foo"));
        assertTrue(PodUtils.isValidName("foo-bar"));
        assertTrue(PodUtils.isValidName("foo.bar"));
        assertTrue(PodUtils.isValidName("foo.123"));
        assertTrue(PodUtils.isValidName("123-foo"));
        assertFalse(PodUtils.isValidName("foo bar"));
        assertFalse(PodUtils.isValidName("-foo"));
        assertFalse(PodUtils.isValidName(".foo"));
    }
}
