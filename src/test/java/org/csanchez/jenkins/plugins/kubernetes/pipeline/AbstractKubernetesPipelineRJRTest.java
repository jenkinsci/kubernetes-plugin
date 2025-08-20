package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.assumeKubernetes;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.CreateWorkflowJobThenScheduleRun;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.RunId;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.SetupCloud;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

abstract class AbstractKubernetesPipelineRJRTest {

    @RegisterExtension
    protected RealJenkinsExtension rjr = new RealJenkinsExtension();

    {
        String port = System.getProperty("port");
        if (StringUtils.isNotBlank(port)) {
            System.err.println("Overriding port using system property: " + port);
            rjr = rjr.withPort(Integer.parseInt(port));
        }
    }

    protected final SetupCloud setup;

    protected String name;

    public AbstractKubernetesPipelineRJRTest(SetupCloud setup) {
        this.setup = setup;
    }

    @BeforeAll
    protected static void beforeAll() {
        assumeKubernetes();
    }

    @BeforeEach
    protected void beforeEach(TestInfo info) throws Throwable {
        name = info.getTestMethod().orElseThrow().getName();
        rjr.startJenkins();
        rjr.runRemotely(setup);
    }

    protected RunId createWorkflowJobThenScheduleRun() throws Throwable {
        return rjr.runRemotely(new CreateWorkflowJobThenScheduleRun(
                KubernetesTestUtil.loadPipelineScript(getClass(), name + ".groovy")));
    }
}
