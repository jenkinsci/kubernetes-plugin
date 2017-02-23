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
        if(job != null) {
            LOGGER.info("Nodes went offline. Hence fetching it through master");
            if (job instanceof AbstractProject) {
                String assignedLabel = ((AbstractProject) job).getAssignedLabelString();
                KubernetesCloud kubernetesCloud = Hudson.getInstance().clouds.get(KubernetesCloud.class);
                if (kubernetesCloud != null) {
                    List<PodTemplate> podTemplates = kubernetesCloud.getTemplates();
                    for (PodTemplate podTemplate : podTemplates) {
                        if (ObjectUtils.equals(podTemplate.getLabel(), assignedLabel)) {
                            FilePath filePath = Jenkins.getInstance().getWorkspaceFor((TopLevelItem) job);
                            if(filePath != null) {
                                String workspacePath = filePath.toString();
                                LOGGER.info("Workspace Path: " + workspacePath);
                                File workspace = new File(workspacePath);
                                LOGGER.info("Workspace exists ? " + workspace.exists());
                                if (workspace.exists()) {
                                    return new FilePath(workspace);
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

