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

import java.util.Arrays;
import java.util.Collection;

import hudson.Extension;
import hudson.model.Run;

@Extension
public class TestJobAnnotationProvider extends PodAnnotationProvider {

    @Override
    public Collection<PodAnnotation> buildFor(Run<?, ?> run) {
        return Arrays.asList(new PodAnnotation("job", run.getParent().getFullName()));
    }

}