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
import org.jenkinsci.plugins.pipeline.modeldefinition.SyntheticStageNames
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
                if (describable.getYamlFile() != null && describable.hasScmContext(script)) {
                    describable.setYaml(script.readTrusted(describable.getYamlFile()))
                }
                script.podTemplate(describable.asArgs) {
                    script.node(describable.label) {
                        def checkoutMap = [:]
                        if (describable.isDoCheckout() && describable.hasScmContext(script)) {
                            String subDir = describable.subdirectory
                            if (subDir != null && subDir != "") {
                                script.dir(subDir) {
                                    checkoutMap.putAll(performCheckout(script, describable))
                                }
                            } else {
                                checkoutMap.putAll(performCheckout(script, describable))
                            }
                        }
                        // what container to use for the main body
                        def container = describable.defaultContainer ?: 'jnlp';

                        if (describable.containerTemplate != null) {
                            // run inside the container declared for backwards compatibility
                            container = describable.containerTemplate.asArgs;
                        }

                        if (checkoutMap) {
                            script.withEnv(checkoutMap.collect { k, v -> "${k}=${v}" }) {
                                // call the main body
                                script.container(container) {
                                    body.call()
                                }
                            }
                        } else {
                            // call the main body
                            script.container(container) {
                                body.call()
                            }
                        }
                    }
                }
            } catch (Exception e) {
                script.getProperty("currentBuild").result = Result.FAILURE
                throw e
            }
        }
    }

    private static Map performCheckout(CpsScript script, KubernetesDeclarativeAgent agent) {
        def checkoutMap = [:]
        if (!agent.inStage) {
            script.stage(SyntheticStageNames.checkout()) {
                checkoutMap.putAll(script.checkout(script.scm) ?: [:])
            }
        } else {
            // No stage when we're in a nested stage already
            checkoutMap.putAll(script.checkout(script.scm) ?: [:])
        }

        return checkoutMap
    }
}
