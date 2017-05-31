package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.csanchez.jenkins.plugins.kubernetes.ContainerTemplateTestUtils.containerTemplate;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class PodTemplateTest {

    @Test
    public void hasSelfRegisteringSlave() throws Exception {
        assertTrue(templateWithSlaveRegisteringContainers(true, true).hasSelfRegisteringSlave());
        assertTrue(templateWithSlaveRegisteringContainers(false, true).hasSelfRegisteringSlave());
        assertFalse(templateWithSlaveRegisteringContainers(false, false).hasSelfRegisteringSlave());
    }


    private PodTemplate templateWithSlaveRegisteringContainers(Boolean... selfRegisteringSlaves) {
        List<ContainerTemplate> containerTemplates = Arrays.stream(selfRegisteringSlaves)
                .map(flag -> containerTemplate("container1", true, flag))
                .collect(Collectors.toList());
        return new PodTemplate("template", Lists.newArrayList(), containerTemplates);
    }

}