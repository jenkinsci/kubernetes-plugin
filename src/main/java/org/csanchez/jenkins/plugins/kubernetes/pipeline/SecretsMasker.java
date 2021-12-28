/*
 * Copyright 2019 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.remoting.Channel;
import hudson.util.LogTaskListener;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.Response;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesComputer;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.credentialsbinding.masking.SecretPatterns;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.DynamicContext;

/**
 * Looks for pod/container secrets and hides them from display.
 */
public final class SecretsMasker extends TaskListenerDecorator {

    private static final Logger LOGGER = Logger.getLogger(SecretsMasker.class.getName());

    private static final long serialVersionUID = 1;

    private final Set<String> values;

    private SecretsMasker(Set<String> values) {
        assert !values.isEmpty();
        this.values = values;
    }

    @Override
    public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
        return new SecretPatterns.MaskingOutputStream(logger, () -> SecretPatterns.getAggregateSecretPattern(values), "UTF-8");
    }

    @Extension
    public static final class Factory extends DynamicContext.Typed<TaskListenerDecorator> {

        private final Map<KubernetesComputer, Set<String>> secrets = new WeakHashMap<>();

        @Override
        protected Class<TaskListenerDecorator> type() {
            return TaskListenerDecorator.class;
        }

        @Override
        protected TaskListenerDecorator get(DelegatedContext context) throws IOException, InterruptedException {
            KubernetesComputer c = context.get(KubernetesComputer.class);
            if (c == null) {
                return null;
            }
            synchronized (secrets) {
                if (secrets.containsKey(c)) {
                    Set<String> values = secrets.get(c);
                    if (values != null) {
                        LOGGER.log(Level.FINE, "Using cached secrets for {0}", c);
                        return TaskListenerDecorator.merge(context.get(TaskListenerDecorator.class), new SecretsMasker(values));
                    } else {
                        LOGGER.log(Level.FINE, "Cached absence of secrets for {0}", c);
                        return null;
                    }
                }
            }
            Set<String> values = secretsOf(c);
            synchronized (secrets) {
                secrets.put(c, values);
            }
            if (values != null) {
                LOGGER.fine("masking " + values.size() + " values");
                return TaskListenerDecorator.merge(context.get(TaskListenerDecorator.class), new SecretsMasker(values));
            } else {
                LOGGER.fine("no values to mask");
                return null;
            }
        }

        private static @CheckForNull Set<String> secretsOf(KubernetesComputer c) throws IOException, InterruptedException {
            Channel ch = c.getChannel();
            if (ch == null) {
                return null;
            }
            KubernetesSlave slave = c.getNode();
            if (slave == null) {
                return null;
            }
            PodTemplate template = slave.getTemplateOrNull();
            if (template == null) {
                return null;
            }
            Pod pod = template.build(slave);
            Set<String> values = new HashSet<>();
            values.add(c.getJnlpMac());
            LOGGER.finer(() -> "inspecting " + Serialization.asYaml(pod));
            for (Container container : pod.getSpec().getContainers()) {
                Set<String> secretContainerKeys = new TreeSet<>();
                List<EnvVar> env = container.getEnv();
                if (env != null) {
                    for (EnvVar envVar : env) {
                        EnvVarSource envVarSource = envVar.getValueFrom();
                        if (envVarSource != null) {
                            SecretKeySelector secretKeySelector = envVarSource.getSecretKeyRef();
                            if (secretKeySelector != null) {
                                secretContainerKeys.add(envVar.getName());
                            }
                        }
                    }
                }
                if (!secretContainerKeys.isEmpty()) {
                    String containerName = container.getName();
                    LOGGER.fine(() -> "looking for " + slave.getNamespace() + "/" + slave.getPodName() + "/" + containerName + " secrets named " + secretContainerKeys);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Semaphore semaphore = new Semaphore(0);
                    Boolean unix = c.isUnix();
                    if (unix == null) {
                        return null;
                    }
                    try (OutputStream errs = new LogTaskListener(LOGGER, Level.FINE).getLogger();
                         ExecWatch exec = slave.getKubernetesCloud().connect().pods().inNamespace(slave.getNamespace()).withName(slave.getPodName()).inContainer(containerName)
                            .writingOutput(baos).writingError(errs).writingErrorChannel(errs)
                            .usingListener(new ExecListener() {
                                @Override
                                public void onOpen(Response response) {
                                }
                                @Override
                                public void onFailure(Throwable t, Response response) {
                                    semaphore.release();
                                }
                                @Override
                                public void onClose(int code, String reason) {
                                    semaphore.release();
                                }
                            })
                            .exec(unix ? new String[] {"env"} : new String[] {"cmd", "/c", "set"})) {
                        if (!semaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                            LOGGER.fine(() -> "time out trying to find environment from " + slave.getNamespace() + "/" + slave.getPodName() + "/" + containerName);
                        }
                    } catch (RuntimeException | KubernetesAuthException x) {
                        LOGGER.log(Level.FINE, "failed to find environment from " + slave.getNamespace() + "/" + slave.getPodName() + "/" + containerName, x);
                    }
                    for (String line : baos.toString(StandardCharsets.UTF_8.name()).split("\r?\n")) {
                        int equals = line.indexOf('=');
                        if (equals != -1) {
                            String key = line.substring(0, equals);
                            if (secretContainerKeys.contains(key)) {
                                LOGGER.fine(() -> "found value for " + key);
                                String value = line.substring(equals + 1);
                                // We add value to set of masked secrets only if it's non-empty not to mask empty strings
                                if (!value.isEmpty()) {
                                    values.add(value);
                                }
                            }
                        }
                    }
                    // TODO issue a warning if we did not find values for all of secretContainerKeys
                }
            }
            return values;
        }

    }

}
