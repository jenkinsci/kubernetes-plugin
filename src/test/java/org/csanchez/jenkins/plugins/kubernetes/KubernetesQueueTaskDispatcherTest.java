package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import java.util.ArrayList;
import java.util.Calendar;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@WithJenkins
@ExtendWith(MockitoExtension.class)
class KubernetesQueueTaskDispatcherTest {

    @Mock
    private ExecutorStepExecution.PlaceholderTask task;

    private Folder folderA;
    private Folder folderB;
    private KubernetesSlave slaveA;
    private KubernetesSlave slaveB;

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    private void setUpTwoClouds() throws Exception {
        folderA = new Folder(j.jenkins, "A");
        folderB = new Folder(j.jenkins, "B");
        j.jenkins.add(folderA, "Folder A");
        j.jenkins.add(folderB, "Folder B");

        KubernetesCloud cloudA = new KubernetesCloud("A");
        cloudA.setUsageRestricted(true);

        KubernetesCloud cloudB = new KubernetesCloud("B");
        cloudB.setUsageRestricted(true);

        j.jenkins.clouds.add(cloudA);
        j.jenkins.clouds.add(cloudB);

        KubernetesFolderProperty property1 = new KubernetesFolderProperty();
        folderA.addProperty(property1);
        JSONObject json1 = new JSONObject();
        json1.element("usage-permission-A", true);
        json1.element("usage-permission-B", false);
        folderA.addProperty(property1.reconfigure((StaplerRequest2) null, json1));

        KubernetesFolderProperty property2 = new KubernetesFolderProperty();
        folderB.addProperty(property2);
        JSONObject json2 = new JSONObject();
        json2.element("usage-permission-A", false);
        json2.element("usage-permission-B", true);
        folderB.addProperty(property2.reconfigure((StaplerRequest2) null, json2));

        slaveA = new KubernetesSlave(
                "A", new PodTemplate(), "testA", "A", "dockerA", new KubernetesLauncher(), RetentionStrategy.INSTANCE);
        slaveB = new KubernetesSlave(
                "B", new PodTemplate(), "testB", "B", "dockerB", new KubernetesLauncher(), RetentionStrategy.INSTANCE);
    }

    @Test
    void checkRestrictedTwoClouds() throws Exception {
        setUpTwoClouds();

        FreeStyleProject projectA = folderA.createProject(FreeStyleProject.class, "buildJob");
        FreeStyleProject projectB = folderB.createProject(FreeStyleProject.class, "buildJob");
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        assertNull(dispatcher.canTake(
                slaveA,
                new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(), projectA, new ArrayList<>()))));
        assertInstanceOf(
                KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed.class, canTake(dispatcher, slaveB, projectA));
        assertInstanceOf(
                KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed.class, canTake(dispatcher, slaveA, projectB));
        assertNull(canTake(dispatcher, slaveB, projectB));
    }

    @Test
    void checkNotRestrictedClouds() throws Exception {
        Folder folder = new Folder(j.jenkins, "C");
        FreeStyleProject project = folder.createProject(FreeStyleProject.class, "buildJob");
        j.jenkins.add(folder, "C");
        KubernetesCloud cloud = new KubernetesCloud("C");
        cloud.setUsageRestricted(false);
        j.jenkins.clouds.add(cloud);
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();
        KubernetesSlave slave = new KubernetesSlave(
                "C", new PodTemplate(), "testC", "C", "dockerC", new KubernetesLauncher(), RetentionStrategy.INSTANCE);

        assertNull(canTake(dispatcher, slave, project));
    }

    @Test
    void checkDumbSlave() throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        FreeStyleProject project = j.createProject(FreeStyleProject.class);
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        assertNull(canTake(dispatcher, slave, project));
    }

    @Test
    void checkPipelinesRestrictedTwoClouds() throws Exception {
        setUpTwoClouds();

        WorkflowJob job = folderA.createProject(WorkflowJob.class, "pipeline");
        when(task.getOwnerTask()).thenReturn(job);
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        assertNull(canTake(dispatcher, slaveA, task));
        assertInstanceOf(
                KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed.class, canTake(dispatcher, slaveB, task));
    }

    private CauseOfBlockage canTake(KubernetesQueueTaskDispatcher dispatcher, Slave slave, Project project) {
        return dispatcher.canTake(
                slave,
                new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(), project, new ArrayList<>())));
    }

    private CauseOfBlockage canTake(KubernetesQueueTaskDispatcher dispatcher, Slave slave, Queue.Task task) {
        return dispatcher.canTake(
                slave, new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(), task, new ArrayList<>())));
    }
}
