package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.*;

import hudson.util.XStream2;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.Test;

/**
 * Test to verify resource limits persist across Jenkins restarts (XStream serialization/deserialization).
 * Related to issue #2766.
 */
public class ResourceLimitPersistenceTest {

    @Test
    public void testContainerTemplateResourceLimitMemoryPersistsAfterXStreamSerialization() {
        // Create container with memory limit
        ContainerTemplate original = new ContainerTemplate("jnlp", "jenkins/inbound-agent");
        original.setResourceLimitMemory("3072Mi");
        original.setResourceLimitCpu("2000m");
        original.setResourceRequestMemory("512Mi");
        original.setResourceRequestCpu("500m");

        // Simulate XStream serialization/deserialization (what happens during Jenkins restart)
        XStream2 xs = new XStream2();
        StringWriter writer = new StringWriter();
        xs.toXML(original, writer);
        String xml = writer.toString();

        System.out.println("Serialized XML:");
        System.out.println(xml);

        // Deserialize
        ContainerTemplate deserialized = (ContainerTemplate) xs.fromXML(new StringReader(xml));

        // Verify resource limits persisted
        assertEquals(
                "Memory limit should persist after deserialization", "3072Mi", deserialized.getResourceLimitMemory());
        assertEquals("CPU limit should persist after deserialization", "2000m", deserialized.getResourceLimitCpu());
        assertEquals(
                "Memory request should persist after deserialization",
                "512Mi",
                deserialized.getResourceRequestMemory());
        assertEquals("CPU request should persist after deserialization", "500m", deserialized.getResourceRequestCpu());
    }

    @Test
    public void testPodTemplateWithContainerResourceLimitsPersists() {
        // Create pod template with container that has memory limits
        ContainerTemplate container = new ContainerTemplate("jnlp", "jenkins/inbound-agent");
        container.setResourceLimitMemory("3072Mi");
        container.setResourceLimitCpu("2000m");

        // Simulate XStream serialization/deserialization
        XStream2 xs = new XStream2();
        StringWriter writer = new StringWriter();
        xs.toXML(container, writer);
        String xml = writer.toString();

        System.out.println("Container Serialized XML:");
        System.out.println(xml);

        // Deserialize
        ContainerTemplate deserialized = (ContainerTemplate) xs.fromXML(new StringReader(xml));

        // Verify container resource limits persisted
        assertEquals("Container memory limit should persist", "3072Mi", deserialized.getResourceLimitMemory());
        assertEquals("Container CPU limit should persist", "2000m", deserialized.getResourceLimitCpu());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testPodTemplateLegacyFieldsMigrateToContainer() {
        // This is the BUG scenario from #2766:
        // 1. User sets memory limit using deprecated PodTemplate.setResourceLimitMemory()
        // 2. At this point, containers list exists but is empty
        // 3. Value gets stored in PodTemplate.resourceLimitMemory (deprecated field)
        // 4. Jenkins restart -> readResolve() should migrate to container

        // Simulate: PodTemplate with deprecated fields set, containers list empty
        String xml = "<org.csanchez.jenkins.plugins.kubernetes.PodTemplate>\n" + "  <id>test-id</id>\n"
                + "  <name>test-pod</name>\n"
                + "  <image>jenkins/inbound-agent</image>\n"
                + "  <privileged>false</privileged>\n"
                + "  <capOnlyOnAlivePods>false</capOnlyOnAlivePods>\n"
                + "  <alwaysPullImage>false</alwaysPullImage>\n"
                + "  <instanceCap>2147483647</instanceCap>\n"
                + "  <slaveConnectTimeout>1000</slaveConnectTimeout>\n"
                + "  <idleMinutes>0</idleMinutes>\n"
                + "  <activeDeadlineSeconds>0</activeDeadlineSeconds>\n"
                + "  <resourceLimitMemory>3072Mi</resourceLimitMemory>\n"
                + "  <resourceLimitCpu>2000m</resourceLimitCpu>\n"
                + "  <resourceRequestMemory>512Mi</resourceRequestMemory>\n"
                + "  <resourceRequestCpu>500m</resourceRequestCpu>\n"
                + "  <volumes/>\n"
                + "  <containers/>\n"
                + // Empty containers list!
                "  <envVars/>\n"
                + "  <annotations/>\n"
                + "  <imagePullSecrets/>\n"
                + "  <agentInjection>false</agentInjection>\n"
                + "</org.csanchez.jenkins.plugins.kubernetes.PodTemplate>";

        System.out.println("Simulating Jenkins restart with this XML (deprecated fields + empty containers):");

        // Deserialize - readResolve() should migrate deprecated fields to container
        XStream2 xs = new XStream2();
        PodTemplate deserialized = (PodTemplate) xs.fromXML(new StringReader(xml));

        // After readResolve(), deprecated fields should be migrated to a NEW container
        System.out.println("After readResolve(), containers: "
                + deserialized.getContainers().size());

        assertFalse(
                "Container should be created from deprecated fields + image",
                deserialized.getContainers().isEmpty());

        ContainerTemplate container = deserialized.getContainers().get(0);
        System.out.println("Container memory limit: " + container.getResourceLimitMemory());
        System.out.println("Container CPU limit: " + container.getResourceLimitCpu());
        System.out.println("Container memory request: " + container.getResourceRequestMemory());
        System.out.println("Container CPU request: " + container.getResourceRequestCpu());

        assertEquals(
                "Memory limit should be migrated from deprecated field", "3072Mi", container.getResourceLimitMemory());
        assertEquals("CPU limit should be migrated from deprecated field", "2000m", container.getResourceLimitCpu());
        assertEquals(
                "Memory request should be migrated from deprecated field",
                "512Mi",
                container.getResourceRequestMemory());
        assertEquals("CPU request should be migrated from deprecated field", "500m", container.getResourceRequestCpu());
    }
}
