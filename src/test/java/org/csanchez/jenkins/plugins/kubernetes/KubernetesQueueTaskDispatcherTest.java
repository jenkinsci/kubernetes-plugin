package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.Project;
import hudson.model.Queue;
import hudson.model.Slave;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class KubernetesQueueTaskDispatcherTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ExecutorStepExecution.PlaceholderTask task;

    private Folder folderA;
    private Folder folderB;
    private KubernetesSlave slaveA;
    private KubernetesSlave slaveB;

    public void setUpTwoClouds() throws Exception {
        folderA = new Folder(jenkins.jenkins, "A");
        folderB = new Folder(jenkins.jenkins, "B");
        jenkins.jenkins.add(folderA, "Folder A");
        jenkins.jenkins.add(folderB, "Folder B");

        KubernetesCloud cloudA = new KubernetesCloud("A");
        cloudA.setUsageRestricted(true);

        KubernetesCloud cloudB = new KubernetesCloud("B");
        cloudB.setUsageRestricted(true);

        jenkins.jenkins.clouds.add(cloudA);
        jenkins.jenkins.clouds.add(cloudB);

        KubernetesFolderProperty property1 = new KubernetesFolderProperty();
        folderA.addProperty(property1);
        JSONObject json1 = new JSONObject();
        json1.element("usage-permission-A", true);
        json1.element("usage-permission-B", false);
        folderA.addProperty(property1.reconfigure(null, json1));

        KubernetesFolderProperty property2 = new KubernetesFolderProperty();
        folderB.addProperty(property2);
        JSONObject json2 = new JSONObject();
        json2.element("usage-permission-A", false);
        json2.element("usage-permission-B", true);
        folderB.addProperty(property2.reconfigure(null, json2));

        slaveA = new KubernetesSlave("A", new PodTemplate(), "testA", "A", "dockerA", new KubernetesLauncher(), RetentionStrategy.INSTANCE);
        slaveB = new KubernetesSlave("B", new PodTemplate(), "testB", "B", "dockerB", new KubernetesLauncher(), RetentionStrategy.INSTANCE);
    }

    public void setUpTwoCloudsPermittedCloudsForm() throws Exception {
        folderA = new Folder(jenkins.jenkins, "A");
        folderB = new Folder(jenkins.jenkins, "B");
        jenkins.jenkins.add(folderA, "Folder A");
        jenkins.jenkins.add(folderB, "Folder B");

        KubernetesCloud cloudA = new KubernetesCloud("A");
        cloudA.setUsageRestricted(true);

        KubernetesCloud cloudB = new KubernetesCloud("B");
        cloudB.setUsageRestricted(true);

        jenkins.jenkins.clouds.add(cloudA);
        jenkins.jenkins.clouds.add(cloudB);

        KubernetesFolderProperty property1 = new KubernetesFolderProperty();
        folderA.addProperty(property1);
        JSONObject json1 = new JSONObject();
        json1.element("permittedClouds", Collections.singletonList("A"));
        folderA.addProperty(property1.reconfigure(null, json1));

        KubernetesFolderProperty property2 = new KubernetesFolderProperty();
        folderB.addProperty(property2);
        JSONObject json2 = new JSONObject();
        json2.element("permittedClouds", Collections.singletonList("B"));
        folderB.addProperty(property2.reconfigure(null, json2));

        slaveA = new KubernetesSlave("A", new PodTemplate(), "testA", "A", "dockerA", new KubernetesLauncher(), RetentionStrategy.INSTANCE);
        slaveB = new KubernetesSlave("B", new PodTemplate(), "testB", "B", "dockerB", new KubernetesLauncher(), RetentionStrategy.INSTANCE);
    }

    @Test
    public void checkRestrictedTwoClouds() throws Exception {
        setUpTwoClouds();

        FreeStyleProject projectA = folderA.createProject(FreeStyleProject.class, "buildJob");
        FreeStyleProject projectB = folderB.createProject(FreeStyleProject.class, "buildJob");
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        assertNull(dispatcher.canTake(slaveA, new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(), projectA, new ArrayList<>()))));
        assertTrue(canTake(dispatcher, slaveB, projectA) instanceof KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed);
        assertTrue(canTake(dispatcher, slaveA, projectB) instanceof KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed);
        assertNull(canTake(dispatcher, slaveB, projectB));
    }

    @Test
    public void checkRestrictedTwoCloudsPermittedCloudsForm() throws Exception {
        // New permittedClouds form for permittedClouds
        setUpTwoCloudsPermittedCloudsForm();

        FreeStyleProject projectA = folderA.createProject(FreeStyleProject.class, "buildJob");
        FreeStyleProject projectB = folderB.createProject(FreeStyleProject.class, "buildJob");
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        assertNull(dispatcher.canTake(slaveA, new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(), projectA, new ArrayList<>()))));
        assertTrue(canTake(dispatcher, slaveB, projectA) instanceof KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed);
        assertTrue(canTake(dispatcher, slaveA, projectB) instanceof KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed);
        assertNull(canTake(dispatcher, slaveB, projectB));
    }

    @Test
    public void checkNotRestrictedClouds() throws Exception {
        Folder folder = new Folder(jenkins.jenkins, "C");
        FreeStyleProject project = folder.createProject(FreeStyleProject.class, "buildJob");
        jenkins.jenkins.add(folder, "C");
        KubernetesCloud cloud = new KubernetesCloud("C");
        cloud.setUsageRestricted(false);
        jenkins.jenkins.clouds.add(cloud);
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();
        KubernetesSlave slave = new KubernetesSlave("C", new PodTemplate(), "testC", "C", "dockerC", new KubernetesLauncher(), RetentionStrategy.INSTANCE);

        assertNull(canTake(dispatcher, slave, project));
    }


    @Test
    public void checkDumbSlave() throws Exception {
        DumbSlave slave = jenkins.createOnlineSlave();
        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class);
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        assertNull(canTake(dispatcher, slave, project));
    }

    @Test
    public void checkPipelinesRestrictedTwoClouds() throws Exception {
        setUpTwoClouds();

        WorkflowJob job = folderA.createProject(WorkflowJob.class, "pipeline");
        when(task.getOwnerTask()).thenReturn(job);
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        assertNull(canTake(dispatcher, slaveA, task));
        assertTrue(canTake(dispatcher, slaveB, task) instanceof KubernetesQueueTaskDispatcher.KubernetesCloudNotAllowed);
    }

    private CauseOfBlockage canTake(KubernetesQueueTaskDispatcher dispatcher, Slave slave, Project project) {
        return dispatcher.canTake(slave, new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(),
                project, new ArrayList<>())));
    }

    private CauseOfBlockage canTake(KubernetesQueueTaskDispatcher dispatcher, Slave slave, Queue.Task task) {
        return dispatcher.canTake(slave, new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(),
                task, new ArrayList<>())));
    }

}
