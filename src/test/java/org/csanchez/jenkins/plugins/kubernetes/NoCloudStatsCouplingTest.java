package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the optional boundary of the cloud-stats integration (JENKINS-67256).
 *
 * <p>The cloud-stats plugin is an <em>optional</em> dependency, so the always-loaded core classes
 * must carry no reference to it — otherwise the plugin fails to load (or an agent fails to
 * deserialize) when cloud-stats is absent. All cloud-stats coupling lives in the dedicated
 * integration package behind {@code @OptionalExtension}. This scans the compiled class files
 * (constant pool included) rather than the source, so a re-introduced import, field type,
 * superinterface, or annotation all trip the guard.
 *
 * <p>Scope: the scan covers the named core classes plus their declared nested classes (each
 * compiled to its own class file), and matches both the internal slash form and the dotted source
 * form of the package — so even a reflective {@code Class.forName("org.jenkinsci.plugins.cloudstats.…")}
 * is caught. Anonymous/synthetic classes and core classes outside the named set are out of scope for
 * this ticket's guard.
 */
class NoCloudStatsCouplingTest {

    /** The forbidden package in both its internal (slash) and dotted (source) forms. */
    private static final List<String> FORBIDDEN =
            List.of("org/jenkinsci/plugins/cloudstats", "org.jenkinsci.plugins.cloudstats");

    @Test
    void coreClassesHaveNoCloudStatsReference() throws IOException {
        for (Class<?> core : List.of(
                KubernetesSlave.class,
                KubernetesComputer.class,
                StandardPlannedNodeBuilder.class,
                PlannedNodeBuilder.class)) {
            for (Class<?> c : withNestedClasses(core)) {
                assertFalse(
                        referencesCloudStats(c),
                        c.getName()
                                + " must not reference cloud-stats; keep that coupling in the optional integration package");
            }
        }
    }

    /** The class itself plus its declared nested classes. */
    private static List<Class<?>> withNestedClasses(Class<?> core) {
        List<Class<?>> classes = new ArrayList<>();
        classes.add(core);
        classes.addAll(List.of(core.getDeclaredClasses()));
        return classes;
    }

    private static boolean referencesCloudStats(Class<?> core) throws IOException {
        String resource = "/" + core.getName().replace('.', '/') + ".class";
        try (InputStream in = core.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("could not locate compiled class file for " + core.getName());
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            // ISO-8859-1 maps every byte to one char, so a UTF-8 constant-pool entry for the forbidden
            // package survives the round-trip verbatim and can be matched as a plain substring.
            String bytecode = new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1);
            return FORBIDDEN.stream().anyMatch(bytecode::contains);
        }
    }
}
