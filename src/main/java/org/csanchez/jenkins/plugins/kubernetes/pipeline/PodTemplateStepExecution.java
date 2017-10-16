package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static java.util.stream.Collectors.*;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodImagePullSecret;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.google.common.base.Strings;

import hudson.AbortException;
import hudson.model.Run;
import hudson.slaves.Cloud;
import io.fabric8.kubernetes.client.KubernetesClient;
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

        PodTemplateAction podTemplateAction = new PodTemplateAction(getContext().get(Run.class));
        NamespaceAction namespaceAction = new NamespaceAction(getContext().get(Run.class));

        //Let's generate a random name based on the user specified to make sure that we don't have
        //issues with concurrent builds, or messing with pre-existing configuration
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String name = String.format(NAME_FORMAT, step.getName(), randString);
        String namespace = checkNamespace(kubernetesCloud, namespaceAction);

        PodTemplate newTemplate = new PodTemplate();
        newTemplate.setName(name);
        newTemplate.setNamespace(namespace);
        newTemplate.setInheritFrom(!Strings.isNullOrEmpty( podTemplateAction.getParentTemplates()) ? podTemplateAction.getParentTemplates() : step.getInheritFrom());
        newTemplate.setInstanceCap(step.getInstanceCap());
        newTemplate.setIdleMinutes(step.getIdleMinutes());
        newTemplate.setSlaveConnectTimeout(step.getSlaveConnectTimeout());
        newTemplate.setLabel(step.getLabel());
        newTemplate.setEnvVars(step.getEnvVars());
        newTemplate.setVolumes(step.getVolumes());
        newTemplate.setCustomWorkspaceVolumeEnabled(step.getWorkspaceVolume() != null);
        newTemplate.setWorkspaceVolume(step.getWorkspaceVolume());
        newTemplate.setContainers(step.getContainers());
        newTemplate.setNodeSelector(step.getNodeSelector());
        newTemplate.setNodeUsageMode(step.getNodeUsageMode());
        newTemplate.setServiceAccount(step.getServiceAccount());
        newTemplate.setAnnotations(step.getAnnotations());
        newTemplate.setImagePullSecrets(
                step.getImagePullSecrets().stream().map(x -> new PodImagePullSecret(x)).collect(toList()));

        if(step.getActiveDeadlineSeconds() != 0) {
            newTemplate.setActiveDeadlineSeconds(step.getActiveDeadlineSeconds());
        }

        kubernetesCloud.addTemplate(newTemplate);
        getContext().newBodyInvoker().withContext(step).withCallback(new PodTemplateCallback(newTemplate)).start();

        podTemplateAction.push(name);
        namespaceAction.push(namespace);
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        new PodTemplateAction(getContext().get(Run.class)).pop();
    }


    private String checkNamespace(KubernetesCloud kubernetesCloud, NamespaceAction namespaceAction) {
        String namespace = null;
        if (!Strings.isNullOrEmpty(step.getNamespace())) {
            namespace = step.getNamespace();
        } else if (!Strings.isNullOrEmpty(namespaceAction.getNamespace())) {
            namespace = namespaceAction.getNamespace();
        } else {
            namespace = kubernetesCloud.getNamespace();
        }
        return namespace;
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
                LOGGER.log(Level.WARNING, "Cloud {0} no longer exists, cannot delete pod template {1}",
                        new Object[] { step.getCloud(), podTemplate.getName() });
                return;
            }
            if (cloud instanceof KubernetesCloud) {
                LOGGER.log(Level.INFO, "Removing pod template and deleting pod {1} from cloud {0}",
                        new Object[] { cloud.name, podTemplate.getName() });
                KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
                kubernetesCloud.removeTemplate(podTemplate);
                KubernetesClient client = kubernetesCloud.connect();
                Boolean deleted = client.pods().withName(podTemplate.getName()).delete();
                if (!Boolean.TRUE.equals(deleted)) {
                    LOGGER.log(Level.WARNING, "Failed to delete pod for agent {0}/{1}: not found",
                            new String[] { client.getNamespace(), podTemplate.getName() });
                    return;
                }
            } else {
                LOGGER.log(Level.WARNING, "Cloud is not a KubernetesCloud: {0} {1}",
                        new String[] { cloud.name, cloud.getClass().getName() });
            }
        }
    }
}
