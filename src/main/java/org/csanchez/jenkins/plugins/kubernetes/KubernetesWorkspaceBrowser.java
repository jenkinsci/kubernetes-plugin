package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import jenkins.model.Jenkins;
import org.apache.commons.lang.ObjectUtils;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;


/**
 * Inspired from https://github.com/jenkinsci/mesos-plugin/blob/master/src/main/java/org/jenkinsci/plugins/mesos/MesosWorkspaceBrowser.java
 */
@Extension
public class KubernetesWorkspaceBrowser extends WorkspaceBrowser{

    private static final Logger LOGGER = Logger.getLogger(KubernetesWorkspaceBrowser.class.getName());

    @Override
    public FilePath getWorkspace(Job job) {
        LOGGER.info("Nodes went offline. Hence fetching workspace through master");
        if (job instanceof AbstractProject) {
            String assignedLabel = ((AbstractProject) job).getAssignedLabelString();
            Jenkins jenkinsInstance = Hudson.getInstanceOrNull();
            if(jenkinsInstance != null) {
                List<KubernetesCloud> kubernetesClouds = jenkinsInstance.clouds.getAll(KubernetesCloud.class);

                //We only care about Kubernetes clouds for the Jenkins instance.
                //Go through the list of Kube clouds and check if we find a match for the job label with any of the pod templates
                for (KubernetesCloud kubernetesCloud : kubernetesClouds) {
                    if (kubernetesCloud != null) {
                        List<PodTemplate> podTemplates = kubernetesCloud.getTemplates();
                        for (PodTemplate podTemplate : podTemplates) {
                            if (ObjectUtils.equals(podTemplate.getLabel(), assignedLabel)) {
                                FilePath filePath = jenkinsInstance.getWorkspaceFor((TopLevelItem) job);
                                if (filePath != null) {
                                    String workspacePath = filePath.toString();
                                    LOGGER.fine("Workspace Path: " + workspacePath);
                                    File workspace = new File(workspacePath);
                                    LOGGER.fine("Workspace exists ? " + workspace.exists());
                                    if (workspace.exists()) {
                                        return new FilePath(workspace);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
