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

import java.util.Collections;

import org.junit.Assert;
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

        Assert.assertEquals("Cloned ContainerTemplate is not equal to the original one!", originalTemplate, clonedTemplate);
        Assert.assertEquals("String representation (toString()) of the cloned and original ContainerTemplate is not equal!",
                originalTemplate.toString(), clonedTemplate.toString());
    }

}
