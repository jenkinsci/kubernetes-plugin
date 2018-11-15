/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.csanchez.jenkins.plugins.kubernetes.pipeline

import hudson.model.Result
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.CheckoutScript
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentScript
import org.jenkinsci.plugins.workflow.cps.CpsScript

public class KubernetesDeclarativeAgentScript extends DeclarativeAgentScript<KubernetesDeclarativeAgent> {
    public KubernetesDeclarativeAgentScript(CpsScript s, KubernetesDeclarativeAgent a) {
        super(s, a)
    }

    @Override
    public Closure run(Closure body) {
        return {
            try {
                if ((describable.getYamlFile() != null) && (describable.hasScmContext(script))) {
                    describable.setYaml(script.readTrusted(describable.getYamlFile()))
                }
                if (describable.getYamlVar() != null) {
                    describable.setYaml(describable.getYamlFile())
                }                
                script.podTemplate(describable.asArgs) {
                    script.node(describable.label) {
                        CheckoutScript.doCheckout(script, describable, describable.customWorkspace) {
                            // what container to use for the main body
                            def container = describable.defaultContainer ?: 'jnlp'

                            if (describable.containerTemplate != null) {
                                // run inside the container declared for backwards compatibility
                                container = describable.containerTemplate.asArgs
                            }

                            // call the main body
                            if (container == 'jnlp') {
                                // If default container is not changed by the pipeline user,
                                // do not enclose the body with a `container` statement.
                                body.call()
                            } else {
                                script.container(container) {
                                    body.call()
                                }
                            }
                        }.call()
                    }
                }
            } catch (Exception e) {
                script.getProperty("currentBuild").result = Result.FAILURE
                throw e
            }
        }
    }
}
