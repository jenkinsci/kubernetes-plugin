package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.net.UnknownHostException;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.AssertBuildLogMessage;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.AssertBuildStatusSuccess;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.CreateWorkflowJobThenScheduleTask;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.RunId;
import org.csanchez.jenkins.plugins.kubernetes.pipeline.steps.SetupCloud;
import org.junit.Test;

public class KubernetesPipelineRJRTest extends AbstractKubernetesPipelineRJRTest {
    public KubernetesPipelineRJRTest() throws UnknownHostException {
        super(new SetupCloud());
    }

    @Test
    public void basicPipeline() throws Throwable {
        RunId runId = createWorkflowJobThenScheduleRun();
        rjr.runRemotely(new AssertBuildStatusSuccess(runId));
    }

    @Test
    public void restartDuringPodLaunch() throws Throwable {
        // try to run something on a pod which is not schedulable (disktype=special)
        RunId build = rjr.runRemotely(new CreateWorkflowJobThenScheduleTask(
                KubernetesTestUtil.loadPipelineScript(getClass(), name.getMethodName() + ".groovy")));
        // the pod is created, but not connected yet
        rjr.runRemotely(new AssertBuildLogMessage("Created Pod", build));
        // restart
        //rjr.stopJenkins();
        //rjr.startJenkins();
        // update k8s to make a node suitable to schedule (add disktype=special to the node)
        System.out.println("Adding label to node....");
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            String nodeName =
                    client.nodes().list().getItems().get(0).getMetadata().getName();
            client.nodes().withName(nodeName).edit(n -> new NodeBuilder(n)
                    .editMetadata()
                    .addToLabels("disktype", "special")
                    .endMetadata()
                    .build());

            // pod connects back and the build finishes correctly
            rjr.runRemotely(new AssertBuildStatusSuccess(build));
        } finally {
            // clean up
            try (KubernetesClient client = new KubernetesClientBuilder().build()) {
                String nodeName =
                        client.nodes().list().getItems().get(0).getMetadata().getName();
                client.nodes().withName(nodeName).edit(n -> new NodeBuilder(n)
                        .editMetadata()
                        .removeFromLabels("disktype")
                        .endMetadata()
                        .build());
            }
        }
    }
}
