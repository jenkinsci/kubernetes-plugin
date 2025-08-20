package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import org.csanchez.jenkins.plugins.kubernetes.pod.retention.OnFailure;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.DynamicPVCWorkspaceVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.EmptyDirWorkspaceVolume;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PodTemplateStepTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-57828")
    @Test
    void configRoundTrip() throws Exception {
        SnippetizerTester st = new SnippetizerTester(r);
        PodTemplateStep step = new PodTemplateStep();

        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setName("podTest");
        st.assertRoundTrip(step, "podTemplate(name: 'podTest') {\n    // some block\n}");
        step.setName("");
        step.setInstanceCap(5);
        st.assertRoundTrip(step, "podTemplate(instanceCap: 5) {\n    // some block\n}");
        step.setInstanceCap(0);
        step.setInstanceCap(6);
        st.assertRoundTrip(step, "podTemplate(instanceCap: 6) {\n    // some block\n}");
        step.setInstanceCap(null); // make sure this resets instanceCap
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setInstanceCap(7);
        st.assertRoundTrip(step, "podTemplate(instanceCap: 7) {\n    // some block\n}");
        step.setInstanceCap(Integer.MAX_VALUE); // make sure this resets instanceCap
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setLabel("podLabel");
        st.assertRoundTrip(step, "podTemplate(label: 'podLabel') {\n    // some block\n}");
        step.setLabel("");
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setPodRetention(
                PodRetention.getPodTemplateDefault()); // this is the default, it should not appear in the snippet.
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setPodRetention(new OnFailure());
        st.assertRoundTrip(step, "podTemplate(podRetention: onFailure()) {\n    // some block\n}");
        step.setPodRetention(null);
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setWorkspaceVolume(new DynamicPVCWorkspaceVolume());
        st.assertRoundTrip(step, "podTemplate(workspaceVolume: dynamicPVC()) {\n    // some block\n}");
        step.setWorkspaceVolume(
                new EmptyDirWorkspaceVolume(false)); // this is the default, it should not be in the snippet.
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        DynamicPVCWorkspaceVolume workspaceVolume = new DynamicPVCWorkspaceVolume();
        workspaceVolume.setAccessModes("ReadWriteMany");
        step.setWorkspaceVolume(workspaceVolume);
        st.assertRoundTrip(
                step, "podTemplate(workspaceVolume: dynamicPVC(accessModes: 'ReadWriteMany')) {\n    // some block\n}");
        step.setWorkspaceVolume(null);
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setActiveDeadlineSeconds(60);
        st.assertRoundTrip(step, "podTemplate(activeDeadlineSeconds: 60) {\n    // some block\n}");
        step.setActiveDeadlineSeconds(0);
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
        step.setInheritFrom("fooBar");
        st.assertRoundTrip(step, "podTemplate(inheritFrom: 'fooBar') {\n    // some block\n}");
        step.setInheritFrom("");
        st.assertRoundTrip(step, "podTemplate(inheritFrom: '') {\n    // some block\n}");
        step.setInheritFrom(null);
        st.assertRoundTrip(step, "podTemplate {\n    // some block\n}");
    }
}
