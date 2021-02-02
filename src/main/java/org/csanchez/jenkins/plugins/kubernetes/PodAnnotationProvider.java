/*
 * Copyright 2021 Falco Nikolas
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
package org.csanchez.jenkins.plugins.kubernetes;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Run;

public class PodAnnotationProvider implements ExtensionPoint {

    @Nonnull
    public Collection<PodAnnotation> buildFor(Run<?, ?> run) {
        return Collections.emptyList();
    }

    @Nonnull
    public Collection<PodAnnotation> buildFor(EnvVars environment) {
        return Collections.emptyList();
    }

    /**
     * All the registered {@link PodAnnotationProvider}s.
     */
    public static ExtensionList<PodAnnotationProvider> all() {
        return ExtensionList.lookup(PodAnnotationProvider.class);
    }

}
