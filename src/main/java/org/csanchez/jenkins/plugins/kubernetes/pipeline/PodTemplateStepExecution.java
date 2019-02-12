package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static java.util.stream.Collectors.*;

import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.apache.commons.lang.RandomStringUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty;
import org.csanchez.jenkins.plugins.kubernetes.Messages;
import org.csanchez.jenkins.plugins.kubernetes.PodImagePullSecret;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.AbortException;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils;

public class PodTemplateStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(PodTemplateStepExecution.class.getName());

    private static final long serialVersionUID = -6139090518333729333L;

    private static final transient String NAME_FORMAT = "%s-%s";

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private final transient PodTemplateStep step;
    private final String cloudName;

    private PodTemplate newTemplate = null;

    PodTemplateStepExecution(PodTemplateStep step, StepContext context) {
        super(context);
        this.step = step;
        this.cloudName = step.getCloud();
    }

    @Override
    public boolean start() throws Exception {

        Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud == null) {
            throw new AbortException(String.format("Cloud does not exist: %s", cloudName));
        }
        if (!(cloud instanceof KubernetesCloud)) {
            throw new AbortException(String.format("Cloud is not a Kubernetes cloud: %s (%s)", cloudName,
                    cloud.getClass().getName()));
        }
        KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;

        Run<?, ?> run = getContext().get(Run.class);
        if (kubernetesCloud.isUsageRestricted()) {
            checkAccess(run, kubernetesCloud);
        }

        PodTemplateContext podTemplateContext = getContext().get(PodTemplateContext.class);
        String parentTemplates = podTemplateContext != null ? podTemplateContext.getName() : null;

        //Let's generate a random name based on the user specified to make sure that we don't have
        //issues with concurrent builds, or messing with pre-existing configuration
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String name = String.format(NAME_FORMAT, step.getName(), randString);
        String namespace = checkNamespace(kubernetesCloud, podTemplateContext);

        newTemplate = new PodTemplate();
        newTemplate.setName(name);
        newTemplate.setNamespace(namespace);
        newTemplate.setInheritFrom(!Strings.isNullOrEmpty(parentTemplates) ? parentTemplates : step.getInheritFrom());
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
        if(run!=null) {
            newTemplate.getAnnotations().add(new PodAnnotation("buildUrl", ((KubernetesCloud)cloud).getJenkinsUrlOrDie()+run.getUrl()));
        }
        newTemplate.setImagePullSecrets(
                step.getImagePullSecrets().stream().map(x -> new PodImagePullSecret(x)).collect(toList()));
        newTemplate.setYaml(step.getYaml());
        newTemplate.setPodRetention(step.getPodRetention());

        if(step.getActiveDeadlineSeconds() != 0) {
            newTemplate.setActiveDeadlineSeconds(step.getActiveDeadlineSeconds());
        }

        for (ContainerTemplate container : newTemplate.getContainers()) {
            if (!PodTemplateUtils.validateContainerName(container.getName())) {
                throw new AbortException(Messages.RFC1123_error(container.getName()));
            }
        }
        Collection<String> errors = PodTemplateUtils.validateYamlContainerNames(newTemplate.getYaml());
        if (!errors.isEmpty()) {
            throw new AbortException(Messages.RFC1123_error(String.join(", ", errors)));
        }

        if (!PodTemplateUtils.validateLabel(newTemplate.getLabel())) {
            throw new AbortException(Messages.label_error(newTemplate.getLabel()));
        }

        kubernetesCloud.addDynamicTemplate(newTemplate);
        getContext().newBodyInvoker().withContexts(step, new PodTemplateContext(namespace, name)).withCallback(new PodTemplateCallback(newTemplate)).start();

        return false;
    }

    /**
     * Check if the current Job is permitted to use the cloud.
     *
     * @param run
     * @param kubernetesCloud
     * @throws AbortException
     *             in case the Job has not been authorized to use the
     *             kubernetesCloud
     */
    private void checkAccess(Run<?, ?> run, KubernetesCloud kubernetesCloud) throws AbortException {
        Job<?, ?> job = run.getParent(); // Return the associated Job for this Build
        ItemGroup<?> parent = job.getParent(); // Get the Parent of the Job (which might be a Folder)

        Set<String> allowedClouds = new HashSet<>();
        KubernetesFolderProperty.collectAllowedClouds(allowedClouds, parent);
        if (!allowedClouds.contains(kubernetesCloud.name)) {
            throw new AbortException(String.format("Not authorized to use Kubernetes cloud: %s", step.getCloud()));
        }
    }

    private String checkNamespace(KubernetesCloud kubernetesCloud, @CheckForNull PodTemplateContext podTemplateContext) {
        String namespace = null;
        if (!Strings.isNullOrEmpty(step.getNamespace())) {
            namespace = step.getNamespace();
        } else if (podTemplateContext != null && !Strings.isNullOrEmpty(podTemplateContext.getNamespace())) {
            namespace = podTemplateContext.getNamespace();
        } else {
            namespace = kubernetesCloud.getNamespace();
        }
        return namespace;
    }

    /**
     * Re-inject the dynamic template when resuming the pipeline
     */
    @Override
    public void onResume() {
        super.onResume();
        Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud == null) {
            throw new RuntimeException(String.format("Cloud does not exist: %s", cloudName));
        }
        if (!(cloud instanceof KubernetesCloud)) {
            throw new RuntimeException(String.format("Cloud is not a Kubernetes cloud: %s (%s)", cloudName,
                    cloud.getClass().getName()));
        }
        KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
        kubernetesCloud.addDynamicTemplate(newTemplate);
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
            Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
            if (cloud == null) {
                LOGGER.log(Level.WARNING, "Cloud {0} no longer exists, cannot delete pod template {1}",
                        new Object[] { cloudName, podTemplate.getName() });
                return;
            }
            if (cloud instanceof KubernetesCloud) {
                LOGGER.log(Level.INFO, "Removing pod template {1} from cloud {0}",
                        new Object[] { cloud.name, podTemplate.getName() });
                KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
                kubernetesCloud.removeDynamicTemplate(podTemplate);
            } else {
                LOGGER.log(Level.WARNING, "Cloud is not a KubernetesCloud: {0} {1}",
                        new String[] { cloud.name, cloud.getClass().getName() });
            }
        }
    }
}
