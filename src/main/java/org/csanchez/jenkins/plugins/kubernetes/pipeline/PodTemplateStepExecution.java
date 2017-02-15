package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.google.common.base.Strings;

import hudson.AbortException;
import hudson.model.Run;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

public class PodTemplateStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(PodTemplateStepExecution.class.getName());

    private static final long serialVersionUID = -6139090518333729333L;

    private static final transient String NAME_FORMAT = "%s-%s";

    private final PodTemplateStep step;

    PodTemplateStepExecution(PodTemplateStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    public boolean start() throws Exception {

        Cloud cloud = Jenkins.getInstance().getCloud(step.getCloud());
        if (cloud == null) {
            throw new AbortException(String.format("Cloud does not exist: %s", step.getCloud()));
        }
        if (!(cloud instanceof KubernetesCloud)) {
            throw new AbortException(String.format("Cloud is not a Kubernetes cloud: %s (%s)", step.getCloud(),
                    cloud.getClass().getName()));
        }
        KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        String name = String.format(NAME_FORMAT, step.getName(), uuid);

        PodTemplateAction action = new PodTemplateAction(getContext().get(Run.class));

        PodTemplate newTemplate = new PodTemplate();
        newTemplate.setName(name);
        newTemplate.setInheritFrom(!Strings.isNullOrEmpty( action.getParentTemplates()) ? action.getParentTemplates() : step.getInheritFrom());
        newTemplate.setInstanceCap(step.getInstanceCap());
        newTemplate.setLabel(step.getLabel());
        newTemplate.setVolumes(step.getVolumes());
        newTemplate.setCustomWorkspaceVolumeEnabled(step.getWorkspaceVolume() != null);
        newTemplate.setWorkspaceVolume(step.getWorkspaceVolume());
        newTemplate.setContainers(step.getContainers());
        newTemplate.setNodeSelector(step.getNodeSelector());
        newTemplate.setServiceAccount(step.getServiceAccount());
        newTemplate.setAnnotations(step.getAnnotations());

        kubernetesCloud.addTemplate(newTemplate);
        getContext().newBodyInvoker().withContext(step).withCallback(new PodTemplateCallback(newTemplate)).start();


        action.push(step.getLabel());
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        new PodTemplateAction(getContext().get(Run.class)).pop();
    }

    private class PodTemplateCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 6043919968776851324L;

        private final PodTemplate podTemplate;

        private PodTemplateCallback(PodTemplate podTemplate) {
            this.podTemplate = podTemplate;
        }

        @Override
        /**
         * Remove the template after step is done
         */
        protected void finished(StepContext context) throws Exception {
            Cloud cloud = Jenkins.getInstance().getCloud(step.getCloud());
            if (cloud == null) {
                LOGGER.log(Level.FINE, "Cloud {0} no longer exists, cannot delete pod template {1}",
                        new Object[] { step.getCloud(), podTemplate.getName() });
                return;
            }
            if (cloud instanceof KubernetesCloud) {
                KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
                kubernetesCloud.removeTemplate(podTemplate);
            }
        }
    }
}
