package org.csanchez.jenkins.plugins.kubernetes;

import org.junit.Test;

import static org.csanchez.jenkins.plugins.kubernetes.SelfRegisteredSlaveRetentionStrategy.getPodName;
import static org.junit.Assert.assertEquals;

/**
 */
public class SelfRegisteredSlaveRetentionStrategyTest {

    @Test
    public void shouldGetPodName() throws Exception {
        assertEquals("pod-1234", getPodName("pod-1234"));
        assertEquals("pod-1234", getPodName("pod-1234-suffix"));
    }

}