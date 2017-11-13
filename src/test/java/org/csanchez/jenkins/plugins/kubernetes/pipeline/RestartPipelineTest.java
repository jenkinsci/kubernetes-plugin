package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import com.google.common.collect.ImmutableMap;
import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.compress.utils.IOUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.model.KeyValueEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.SecretEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsNonLocalhostRule;

import java.net.InetAddress;
import java.net.URL;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Arrays.asList;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assumeKubernetes;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.setupCloud;

public class RestartPipelineTest {
    protected static final String CONTAINER_ENV_VAR_VALUE = "container-env-var-value";
    protected static final String POD_ENV_VAR_VALUE = "pod-env-var-value";
    protected static final String SECRET_KEY = "password";
    protected static final String CONTAINER_ENV_VAR_FROM_SECRET_VALUE = "container-pa55w0rd";
    protected static final String POD_ENV_VAR_FROM_SECRET_VALUE = "pod-pa55w0rd";
    protected KubernetesCloud cloud;

    @Rule
    public RestartableJenkinsNonLocalhostRule story = new RestartableJenkinsNonLocalhostRule(44000);
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public LoggerRule logs = new LoggerRule().record(Logger.getLogger(KubernetesCloud.class.getPackage().getName()),
           Level.ALL);
    //.record("org.jenkinsci.plugins.durabletask", Level.ALL).record("org.jenkinsci.plugins.workflow.support.concurrent", Level.ALL).record("org.csanchez.jenkins.plugins.kubernetes.pipeline", Level.ALL);

    @BeforeClass
    public static void isKubernetesConfigured() throws Exception {
        assumeKubernetes();
    }

    private static void createSecret(KubernetesClient client) {
        Secret secret = new SecretBuilder()
                .withStringData(ImmutableMap.of(SECRET_KEY, CONTAINER_ENV_VAR_FROM_SECRET_VALUE)).withNewMetadata()
                .withName("container-secret").endMetadata().build();
        client.secrets().createOrReplace(secret);
        secret = new SecretBuilder().withStringData(ImmutableMap.of(SECRET_KEY, POD_ENV_VAR_FROM_SECRET_VALUE))
                .withNewMetadata().withName("pod-secret").endMetadata().build();
        client.secrets().createOrReplace(secret);
    }

    private static void setEnvVariables(PodTemplate podTemplate) {
        TemplateEnvVar podSecretEnvVar = new SecretEnvVar("POD_ENV_VAR_FROM_SECRET", "pod-secret", SECRET_KEY);
        TemplateEnvVar podSimpleEnvVar = new KeyValueEnvVar("POD_ENV_VAR", POD_ENV_VAR_VALUE);
        podTemplate.setEnvVars(asList(podSecretEnvVar, podSimpleEnvVar));
        TemplateEnvVar containerEnvVariable = new KeyValueEnvVar("CONTAINER_ENV_VAR", CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerEnvVariableLegacy = new ContainerEnvVar("CONTAINER_ENV_VAR_LEGACY",
                CONTAINER_ENV_VAR_VALUE);
        TemplateEnvVar containerSecretEnvVariable = new SecretEnvVar("CONTAINER_ENV_VAR_FROM_SECRET",
                "container-secret", SECRET_KEY);
        podTemplate.getContainers().get(0)
                .setEnvVars(asList(containerEnvVariable, containerEnvVariableLegacy, containerSecretEnvVariable));
    }

    private PodTemplate buildBusyboxTemplate(String label) {
        // Create a busybox template
        PodTemplate podTemplate = new PodTemplate();
        podTemplate.setLabel(label);

        ContainerTemplate containerTemplate = new ContainerTemplate("busybox", "busybox", "cat", "");
        containerTemplate.setTtyEnabled(true);
        podTemplate.getContainers().add(containerTemplate);
        setEnvVariables(podTemplate);
        return podTemplate;
    }

    public void configureCloud() throws Exception {
        cloud = setupCloud();
        createSecret(cloud.connect());
        cloud.getTemplates().clear();
        cloud.addTemplate(buildBusyboxTemplate("busybox"));

        // Slaves running in Kubernetes (minikube) need to connect to this server, so localhost does not work
        URL url = story.j.getURL();

        String hostAddress = System.getProperty("jenkins.host.address");
        if (hostAddress == null) {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        }
        URL nonLocalhostUrl = new URL(url.getProtocol(), hostAddress, url.getPort(),
                url.getFile());
        JenkinsLocationConfiguration.get().setUrl(nonLocalhostUrl.toString());

        story.j.jenkins.clouds.add(cloud);
    }

    protected String loadPipelineScript(String name) {
        try {
            return new String(IOUtils.toByteArray(getClass().getResourceAsStream(name)));
        } catch (Throwable t) {
            throw new RuntimeException("Could not read resource:[" + name + "].");
        }
    }

    @Test
    public void runInPodWithRestartWithMultipleContainerCalls() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {

                configureCloud();

                story.j.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1",
                        Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP,
                        Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithRestartWithMultipleContainerCalls.groovy")
                        , true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                SemaphoreStep.success("wait/1", false);
                // we need to wait until we are sure that the sh
                // step has started...
                Thread.sleep(10000);
            }
        });
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun b = story.j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
                story.j.assertLogContains("finished the test!", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Test
    public void runInPodWithRestart() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {

                configureCloud();

                story.j.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1",
                        Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP,
                        Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(loadPipelineScript("runInPodWithRestartWithLongSleep.groovy")
                        , true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                SemaphoreStep.success("wait/1", false);
                // we need to wait until we are sure that the sh
                // step has started...
                Thread.sleep(10000);
            }
        });
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun b = story.j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
                story.j.assertLogContains("finished the test!", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Test
    public void getContainerLogWithRestart() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {

                configureCloud();

                story.j.jenkins.addNode(new DumbSlave("slave", "dummy", tmp.newFolder("remoteFS").getPath(), "1",
                        Node.Mode.NORMAL, "", new JNLPLauncher(), RetentionStrategy.NOOP,
                        Collections.<NodeProperty<?>>emptyList())); // TODO JENKINS-26398 clumsy
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(loadPipelineScript("getContainerLogWithRestart.groovy")
                        , true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                SemaphoreStep.success("wait/1", false);
                Thread.sleep(1000);
            }
        });
        story.addStep(new Statement() {
            @SuppressWarnings("SleepWhileInLoop")
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun b = story.j.jenkins.getItemByFullName("p", WorkflowJob.class).getBuildByNumber(1);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("INFO: Handshaking", b);
                story.j.assertLogContains("INFO: Connected", b);
            }
        });
    }
}
