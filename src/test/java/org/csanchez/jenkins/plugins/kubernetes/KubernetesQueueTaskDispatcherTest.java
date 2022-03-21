package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class KubernetesQueueTaskDispatcherTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();


    @Test
    public void checkRestrictedTwoClouds() throws Exception {

        Folder folder1 = new Folder(jenkins.jenkins, "A");
        Folder folder2 = new Folder(jenkins.jenkins, "B");

        FreeStyleProject project1 = folder1.createProject(FreeStyleProject.class, "buildJob");
        FreeStyleProject project2 = folder2.createProject(FreeStyleProject.class, "buildJob");

        jenkins.jenkins.add(folder1, "A");
        jenkins.jenkins.add(folder2, "B");

        KubernetesCloud cloud1 = new KubernetesCloud("A");
        cloud1.setUsageRestricted(true);

        KubernetesCloud cloud2 = new KubernetesCloud("B");
        cloud2.setUsageRestricted(true);


        jenkins.jenkins.clouds.add(cloud1);
        jenkins.jenkins.clouds.add(cloud2);

        KubernetesFolderProperty property1 = new KubernetesFolderProperty();
        folder1.addProperty(property1);
        JSONObject json1 = new JSONObject();
        json1.element("usage-permission-A", true);
        json1.element("usage-permission-B", false);
        folder1.addProperty(property1.reconfigure(null, json1));


        KubernetesFolderProperty property2 = new KubernetesFolderProperty();
        folder2.addProperty(property2);
        JSONObject json2 = new JSONObject();
        json2.element("usage-permission-A", false);
        json2.element("usage-permission-B", true);
        folder2.addProperty(property2.reconfigure(null, json2));

        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();
        KubernetesSlave slave1 = new KubernetesSlave("A", new PodTemplate(), "testA", "A", "dockerA", new KubernetesLauncher(), RetentionStrategy.INSTANCE);
        KubernetesSlave slave2 = new KubernetesSlave("B", new PodTemplate(), "testB", "B", "dockerB", new KubernetesLauncher(), RetentionStrategy.INSTANCE);

        assertNull(dispatcher.canTake(slave1, project1));
        assertNotNull(dispatcher.canTake(slave2, project1));

        assertNotNull(dispatcher.canTake(slave1, project2));
        assertNull(dispatcher.canTake(slave2, project2));


    }

    @Test
    public void checkNotRestrictedClouds() throws Exception {

        Folder folder = new Folder(jenkins.jenkins, "A");

        FreeStyleProject project = folder.createProject(FreeStyleProject.class, "buildJob");

        jenkins.jenkins.add(folder, "A");

        KubernetesCloud cloud = new KubernetesCloud("A");
        cloud.setUsageRestricted(false);

        jenkins.jenkins.clouds.add(cloud);



        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();
        KubernetesSlave slave = new KubernetesSlave("A", new PodTemplate(), "testA", "A", "dockerA", new KubernetesLauncher(), RetentionStrategy.INSTANCE);

        assertNull(dispatcher.canTake(slave, project));
    }


    @Test
    public void checkDumbSlave() throws Exception {
        DumbSlave slave = jenkins.createOnlineSlave();
        KubernetesQueueTaskDispatcher dispatcher = new KubernetesQueueTaskDispatcher();

        FreeStyleProject project = jenkins.createProject(FreeStyleProject.class);

        assertNull(dispatcher.canTake(slave, project));

    }

}
