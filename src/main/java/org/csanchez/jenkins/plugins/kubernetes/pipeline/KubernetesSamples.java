/*
 * Copyright 2020 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import hudson.ExtensionComponent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jenkins.ExtensionFilter;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.variant.OptionalExtension;
import org.jenkinsci.plugins.workflow.cps.GroovySample;

public class KubernetesSamples {

    @OptionalExtension(requirePlugins = "workflow-cps")
    public static final class SuppressToolBasedSamples extends ExtensionFilter {

        @Override
        public <T> boolean allows(Class<T> type, ExtensionComponent<T> component) {
            if (type == GroovySample.class) {
                switch (((GroovySample) component.getInstance()).name()) {
                    case "github-maven":
                    case "scripted":
                        return false;
                    default:
                        // OK
                }
            }
            return true;
        }
    }

    private abstract static class Static implements GroovySample {

        @Override
        public String script() {
            try {
                return IOUtils.toString(
                        KubernetesSamples.class.getResource(
                                "samples/" + name().replaceFirst("^kubernetes-", "") + ".groovy"),
                        StandardCharsets.UTF_8);
            } catch (IOException x) {
                throw new AssertionError(x);
            }
        }
    }

    @OptionalExtension(requirePlugins = "workflow-cps", ordinal = 1500)
    public static final class Declarative extends Static {

        @Override
        public String name() {
            return "kubernetes-declarative";
        }

        @Override
        public String title() {
            return "Declarative (Kubernetes)";
        }
    }

    @OptionalExtension(requirePlugins = "workflow-cps", ordinal = 1400)
    public static final class Maven extends Static {

        @Override
        public String name() {
            return "kubernetes-maven";
        }

        @Override
        public String title() {
            return "Maven (Kubernetes)";
        }
    }

    @OptionalExtension(requirePlugins = "workflow-cps", ordinal = 1300)
    public static final class Windows extends Static {

        @Override
        public String name() {
            return "kubernetes-windows";
        }

        @Override
        public String title() {
            return "Windows (Kubernetes)";
        }
    }

    private KubernetesSamples() {}
}
