package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.substitute;
import static org.junit.Assert.assertEquals;

public class SubstituteTest {


    @Test
    public void shouldIgnoreMissingProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        assertEquals("${key2}", substitute("${key2}", properties));
    }

    @Test
    public void shouldSubstituteSingleEnvVar() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        assertEquals("value1", substitute("${key1}", properties));
    }

    @Test
    public void shouldSubstituteMultipleEnvVars() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");
        assertEquals("value1 or value2", substitute("${key1} or ${key2}", properties));
    }

    @Test
    public void shouldSubstituteMultipleEnvVarsAndIgnoreMissing() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");
        assertEquals("value1 or value2 or ${key3}", substitute("${key1} or ${key2} or ${key3}", properties));
    }

    @Test
    public void shouldSubstituteMultipleEnvVarsAndUseDefaultsForMissing() {
        Map<String, String> properties = new HashMap<>();
        properties.put("key1", "value1");
        properties.put("key2", "value2");
        assertEquals("value1 or value2 or defaultValue", substitute("${key1} or ${key2} or ${key3}", properties, "defaultValue"));
    }
}
