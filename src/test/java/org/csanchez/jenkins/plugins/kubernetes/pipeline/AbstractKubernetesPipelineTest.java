/*
 * The MIT License
 *
 * Copyright (c) 2016, Carlos Sanchez
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

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static java.util.Arrays.*;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import hudson.slaves.NodeProvisioner;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.jenkins.plugins.kubernetes.NoDelayProvisionerStrategy;
import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;
import org.jvnet.hudson.test.LoggerRule;

import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;

public abstract class AbstractKubernetesPipelineTest {
    protected static final String CONTAINER_ENV_VAR_VALUE = "container-env-var-value";
    protected static final String POD_ENV_VAR_VALUE = "pod-env-var-value";
    protected static final String GLOBAL = "GLOBAL";

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    protected KubernetesCloud cloud;

    @Rule
    public JenkinsRuleNonLocalhost r = new JenkinsRuleNonLocalhost();
    @Rule
    public LoggerRule logs = new LoggerRule()
            .recordPackage(KubernetesCloud.class, Level.FINE)
            .recordPackage(NoDelayProvisionerStrategy.class, Level.FINE)
            .record(NodeProvisioner.class, Level.FINE);

    @BeforeClass
    public static void isKubernetesConfigured() throws Exception {
        assumeKubernetes();
    }

    @Rule
    public TestName name = new TestName();

    private String projectName;

    protected WorkflowJob p;

    protected WorkflowRun b;

    @Before
    public void defineProjectName() {
        // Add spaces before uppercases
        this.projectName = generateProjectName(name.getMethodName());
    }

    protected String getProjectName() {
        return projectName;
    }

    /**
     * Creates a pipeline job using <methodName>.groovy as pipeline definition,
     * then schedule it and wait for it to start.
     *
     * Resolves $NAME to the method name in order to avoid any hard-coded reference
     * to the method name within the pipeline definition.
     *
     * @return The scheduled pipeline run
     * @throws IOException If something gets wrong when creating the pipeline job
     * @throws ExecutionException If something went wrong while retrieving the run object
     * @throws InterruptedException If the thread gets interrupted while waiting for the run to start
     */
    protected final WorkflowRun createJobThenScheduleRun() throws IOException, ExecutionException, InterruptedException {
        return createJobThenScheduleRun(null);
    }

    /**
     * Creates a pipeline job using <methodName>.groovy as pipeline definition,
     * then schedule it and wait for it to start.
     *
     * Resolves $NAME to the method name in order to avoid any hard-coded reference
     * to the method name within the pipeline definition. Also resolves any reference provided in the given env map.
     *
     * @param env an environment map to resolve in the pipeline script
     * @return The scheduled pipeline run
     * @throws IOException If something gets wrong when creating the pipeline job
     * @throws ExecutionException If something went wrong while retrieving the run object
     * @throws InterruptedException If the thread gets interrupted while waiting for the run to start
     */
    protected final WorkflowRun createJobThenScheduleRun(Map<String, String> env) throws IOException, ExecutionException, InterruptedException {
        b = createPipelineJobThenScheduleRun(r, getClass(), name.getMethodName(), env);
        p = b.getParent();
        return b;
    }

    protected final String loadPipelineDefinition() {
        return KubernetesTestUtil.loadPipelineDefinition(getClass(), name.getMethodName(), null);
    }

    @Before
    public void configureCloud() throws Exception {
        cloud = setupCloud(this, name);
        createSecret(cloud.connect(), cloud.getNamespace());
        cloud.getTemplates().clear();
        cloud.addTemplate(buildBusyboxTemplate("busybox"));

        setupHost();

        r.jenkins.clouds.add(cloud);

        DescribableList<NodeProperty<?>, NodePropertyDescriptor> list =  r.jenkins.getGlobalNodeProperties();
        EnvironmentVariablesNodeProperty newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
        list.add(newEnvVarsNodeProperty);
        EnvVars envVars = newEnvVarsNodeProperty.getEnvVars();
        envVars.put("GLOBAL", "GLOBAL");
        envVars.put("JAVA_HOME_X", "java-home-x");
        r.jenkins.save();
    }

    private PodTemplate buildBusyboxTemplate(String label) {
        // Create a busybox template
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(label);
        podTemplate.setTerminationGracePeriodSeconds(0L);

        ContainerTemplate containerTemplate = new ContainerTemplate("busybox", "busybox", "cat", "");
        containerTemplate.setTtyEnabled(true);
        podTemplate.getContainers().add(containerTemplate);
        setEnvVariables(podTemplate);
        return podTemplate;
    }

    protected String loadPipelineScript(String name) {
        return KubernetesTestUtil.loadPipelineScript(getClass(), name);
    }

    private static void setEnvVariables(PodTemplate podTemplate) {
        TemplateEnvVar podSecretEnvVar = new SecretEnvVar("POD_ENV_VAR_FROM_SECRET", "pod-secret", SECRET_KEY, false);
        TemplateEnvVar podSimpleEnvVar = new KeyValueEnvVar("POD_ENV_VAR", POD_ENV_VAR_VALUE);
        podTemplate.setEnvVars(asList(podSecretEnvVar, podSimpleEnvVar));
        TemplateEnvVar containerEnvVariable = new KeyValueEnvVar("CONTAINER_ENV_VAR", CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerEnvVariableLegacy = new ContainerEnvVar("CONTAINER_ENV_VAR_LEGACY",
                CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerSecretEnvVariable = new SecretEnvVar("CONTAINER_ENV_VAR_FROM_SECRET",
                                                                     "container-secret", SECRET_KEY, false);
        podTemplate.getContainers().get(0)
                .setEnvVars(asList(containerEnvVariable, containerEnvVariableLegacy, containerSecretEnvVariable));
    }

    protected void createNamespaceIfNotExist(KubernetesClient client, String namespace) {
        if (client.namespaces().withName(namespace).get() == null) {
            client.namespaces().createOrReplace(
                    new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());
        }
    }
}
