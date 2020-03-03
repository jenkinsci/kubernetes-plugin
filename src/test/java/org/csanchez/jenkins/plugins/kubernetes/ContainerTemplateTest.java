/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes;

import hudson.util.FormValidation;
import java.util.Collections;

import static org.junit.Assert.*;
import org.junit.Test;

public class ContainerTemplateTest {

    @Test
    public void testCopyConstructorCreatesEqualInstance() {
        ContainerTemplate originalTemplate = new ContainerTemplate("myname", "myimage");
        originalTemplate.setPrivileged(true);
        originalTemplate.setAlwaysPullImage(true);
        originalTemplate.setWorkingDir("some/bogus/dir");
        originalTemplate.setCommand("run this");
        originalTemplate.setArgs("args");
        originalTemplate.setTtyEnabled(true);
        originalTemplate.setResourceRequestCpu("200m");
        originalTemplate.setResourceRequestMemory("2GiB");
        originalTemplate.setResourceLimitCpu("1000m");
        originalTemplate.setResourceLimitMemory("4GiB");
        originalTemplate.setShell("zsh");
        originalTemplate.setEnvVars(Collections.emptyList());
        originalTemplate.setPorts(Collections.emptyList());
        originalTemplate.setLivenessProbe(new ContainerLivenessProbe("test", 1, 2, 3, 4, 5));

        ContainerTemplate clonedTemplate = new ContainerTemplate(originalTemplate);

        assertEquals("Cloned ContainerTemplate is not equal to the original one!", originalTemplate, clonedTemplate);
        assertEquals("String representation (toString()) of the cloned and original ContainerTemplate is not equal!",
                originalTemplate.toString(), clonedTemplate.toString());
    }

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    @Test
    public void badImage() throws Exception {
        new ContainerTemplate("n", "something");
        assertEquals(FormValidation.Kind.OK, new ContainerTemplate.DescriptorImpl().doCheckImage("something").kind);
        for (String empty : new String[] {null, ""}) {
            assertThrows("rejected " + empty, IllegalArgumentException.class, () -> new ContainerTemplate("n", empty));
            assertEquals("tolerating " + empty + " during form validation", FormValidation.Kind.OK, new ContainerTemplate.DescriptorImpl().doCheckImage(empty).kind);
        }
        for (String bad : new String[] {" ", " something"}) {
            assertThrows("rejected " + bad, IllegalArgumentException.class, () -> new ContainerTemplate("n", bad));
            assertEquals("rejected " + bad, FormValidation.Kind.ERROR, new ContainerTemplate.DescriptorImpl().doCheckImage(bad).kind);
        }
    }

}
