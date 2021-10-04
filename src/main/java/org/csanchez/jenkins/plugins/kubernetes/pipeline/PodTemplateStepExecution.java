package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import org.apache.commons.lang.RandomStringUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesFolderProperty;
import org.csanchez.jenkins.plugins.kubernetes.Messages;
import org.csanchez.jenkins.plugins.kubernetes.PodImagePullSecret;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.AbortException;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.slaves.Cloud;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodAnnotation;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

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
        KubernetesCloud cloud = resolveCloud();

        Run<?, ?> run = getContext().get(Run.class);
        if (cloud.isUsageRestricted()) {
            checkAccess(run, cloud);
        }

        PodTemplateContext podTemplateContext = getContext().get(PodTemplateContext.class);
        String parentTemplates = podTemplateContext != null ? podTemplateContext.getName() : null;

        String label = step.getLabel();
        if (label == null) {
            label = labelify(run.getExternalizableId());
        }

        //Let's generate a random name based on the user specified to make sure that we don't have
        //issues with concurrent builds, or messing with pre-existing configuration
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String stepName = step.getName();
        if (stepName == null) {
            stepName = label;
        }
        String name = String.format(NAME_FORMAT, stepName, randString);
        String namespace = checkNamespace(cloud, podTemplateContext);

        newTemplate = new PodTemplate();
        newTemplate.setName(name);
        newTemplate.setNamespace(namespace);

        if (step.getInheritFrom() == null) {
            newTemplate.setInheritFrom(PodTemplateUtils.emptyToNull(parentTemplates));
        } else {
            newTemplate.setInheritFrom(PodTemplateUtils.emptyToNull(step.getInheritFrom()));
        }
        newTemplate.setInstanceCap(step.getInstanceCap());
        newTemplate.setIdleMinutes(step.getIdleMinutes());
        newTemplate.setSlaveConnectTimeout(step.getSlaveConnectTimeout());
        newTemplate.setLabel(label);
        newTemplate.setEnvVars(step.getEnvVars());
        newTemplate.setVolumes(step.getVolumes());
        if (step.getWorkspaceVolume() != null) {
            newTemplate.setWorkspaceVolume(step.getWorkspaceVolume());
        }
        newTemplate.setContainers(step.getContainers());
        newTemplate.setNodeSelector(step.getNodeSelector());
        newTemplate.setNodeUsageMode(step.getNodeUsageMode());
        newTemplate.setServiceAccount(step.getServiceAccount());
        newTemplate.setSchedulerName(step.getSchedulerName());
        newTemplate.setRunAsUser(step.getRunAsUser());
        newTemplate.setRunAsGroup(step.getRunAsGroup());
        if (step.getHostNetwork() != null) {
            newTemplate.setHostNetwork(step.getHostNetwork());
        }
        newTemplate.setAnnotations(step.getAnnotations());
        newTemplate.setListener(getContext().get(TaskListener.class));
        newTemplate.setYamlMergeStrategy(step.getYamlMergeStrategy());
        if(run!=null) {
            String url = cloud.getJenkinsUrlOrNull();
            if(url != null) {
                newTemplate.getAnnotations().add(new PodAnnotation("buildUrl", url + run.getUrl()));
                newTemplate.getAnnotations().add(new PodAnnotation("runUrl", run.getUrl()));
            }
        }
        newTemplate.setImagePullSecrets(
                step.getImagePullSecrets().stream().map(x -> new PodImagePullSecret(x)).collect(toList()));
        newTemplate.setYaml(step.getYaml());
        if (step.isShowRawYamlSet()) {
            newTemplate.setShowRawYaml(step.isShowRawYaml());
        }
        newTemplate.setPodRetention(step.getPodRetention());

        if(step.getActiveDeadlineSeconds() != 0) {
            newTemplate.setActiveDeadlineSeconds(step.getActiveDeadlineSeconds());
        }

        for (ContainerTemplate container : newTemplate.getContainers()) {
            if (!PodTemplateUtils.validateContainerName(container.getName())) {
                throw new AbortException(Messages.RFC1123_error(container.getName()));
            }
        }
        Collection<String> errors = PodTemplateUtils.validateYamlContainerNames(newTemplate.getYamls());
        if (!errors.isEmpty()) {
            throw new AbortException(Messages.RFC1123_error(String.join(", ", errors)));
        }

        cloud.addDynamicTemplate(newTemplate);
        BodyInvoker invoker = getContext().newBodyInvoker().withContexts(step, new PodTemplateContext(namespace, name)).withCallback(new PodTemplateCallback(newTemplate));
        if (step.getLabel() == null) {
            invoker.withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), EnvironmentExpander.constant(Collections.singletonMap("POD_LABEL", label))));
        }
        invoker.start();

        return false;
    }

    @NonNull
    private KubernetesCloud resolveCloud() throws AbortException {
        KubernetesCloud cloud;
        if (cloudName == null) {
            cloud = Jenkins.get().clouds.get(KubernetesCloud.class);
            if (cloud == null) {
                throw new AbortException("No Kubernetes cloud was found.");
            }
        } else {
            Cloud cl = Jenkins.get().getCloud(cloudName);
            if (cl == null) {
                throw new AbortException(String.format("Cloud does not exist: %s", cloudName));
            }
            if (!(cl instanceof KubernetesCloud)) {
                throw new AbortException(String.format("Cloud is not a Kubernetes cloud: %s (%s)", cloudName,
                        cl.getClass().getName()));
            }
            cloud = (KubernetesCloud) cl;
        }
        return cloud;
    }

    static String labelify(String input) {
        int max = /* Kubernetes limit */ 63 - /* hyphen */ 1 - /* suffix */ 5;
        if (input.length() > max) {
            input = input.substring(input.length() - max);
        }
        input = input.replaceAll("[^_a-zA-Z0-9-]", "_").replaceFirst("^[^a-zA-Z0-9]", "x");
        String label = input + "-" + RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        assert PodTemplateUtils.validateLabel(label) : label;
        return label;
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
        if (!PodTemplateUtils.isNullOrEmpty(step.getNamespace())) {
            namespace = step.getNamespace();
        } else if (podTemplateContext != null && !PodTemplateUtils.isNullOrEmpty(podTemplateContext.getNamespace())) {
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
        try {
            KubernetesCloud cloud = resolveCloud();
            TaskListener listener = getContext().get(TaskListener.class);
            newTemplate.setListener(listener);
            LOGGER.log(Level.FINE, "Injecting template after resume: " + newTemplate);
            cloud.addDynamicTemplate(newTemplate);
        } catch (AbortException e) {
            throw new RuntimeException(e.getMessage(), e.getCause());
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Unable to inject task listener", e);
        }
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
            try {
                KubernetesCloud cloud = resolveCloud();
                LOGGER.log(Level.FINE, () -> "Removing pod template " + podTemplate.getName()
                        + " from cloud " + cloud.name);
                cloud.removeDynamicTemplate(podTemplate);
            } catch (AbortException e) {
                LOGGER.log(Level.WARNING, e, () -> "Unable to resolve cloud for " + podTemplate.getName() + ". Maybe the cloud was removed while running the build?");
            }
        }
    }
}
