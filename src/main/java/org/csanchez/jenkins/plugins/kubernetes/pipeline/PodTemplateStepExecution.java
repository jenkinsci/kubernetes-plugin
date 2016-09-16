package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.util.Collection;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.ContainerTemplate;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodVolumes;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.model.Label;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

public class PodTemplateStepExecution extends AbstractStepExecutionImpl {

    private static final transient String NAME_FORMAT = "kubernetes-%s";

    private static final String DEFAULT_JNLP_IMAGE = System
            .getProperty(PodTemplateStepExecution.class.getName() + ".defaultImage", "jenkinsci/jnlp-slave:alpine");

    @Inject
    private PodTemplateStep step;

    @Override
    public boolean start() throws Exception {

        Cloud cloud = Jenkins.getActiveInstance().getCloud(step.getCloud());
        if (cloud instanceof KubernetesCloud) {
            KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;

            PodTemplate newTemplate;
            String name = String.format(NAME_FORMAT, UUID.randomUUID().toString().replaceAll("-", ""));

            PodTemplate podTemplate = StringUtils.isBlank(step.getInheritFrom())
                    ? null
                    : kubernetesCloud.getTemplate(Label.get(step.getInheritFrom()));

            if (podTemplate != null) {
                newTemplate = new PodTemplate(podTemplate);
                newTemplate.getContainers().addAll(getContainersWithDefaults(step));
                for (PodVolumes.PodVolume volume : step.getVolumes()) {
                    String mountPath = volume.getMountPath();
                    if (!PodVolumes.podVolumeExists(mountPath, podTemplate.getVolumes())) {
                        newTemplate.getVolumes().add(volume);
                    }
                }
            } else {
                newTemplate = new PodTemplate();
                newTemplate.setVolumes(step.getVolumes());
                newTemplate.setContainers(step.getContainers());
            }

            newTemplate.setLabel(step.getLabel());
            newTemplate.setName(name);

            kubernetesCloud.addTemplate(newTemplate);
            getContext().newBodyInvoker()
                    .withCallback(new PodTemplateCallback(newTemplate))
                    .start();
            return false;
        } else {
            getContext().onFailure(new IllegalStateException("Could not find cloud with name:[" + step.getCloud() + "]."));
            return true;
        }
    }

    private Collection<? extends ContainerTemplate> getContainersWithDefaults(PodTemplateStep step) {
        if (step.getContainers().stream().noneMatch(c -> "jnlp".equals(c.getName()))) {
            ContainerTemplate containerTemplate = new ContainerTemplate(DEFAULT_JNLP_IMAGE);
            containerTemplate.setName("jnlp");
            containerTemplate.setArgs("${computer.jnlpmac} ${computer.name}");
            step.getContainers().add(containerTemplate);
        }
        return step.getContainers();
    }

    @Override
    public void stop(Throwable cause) throws Exception {
    }

    private class PodTemplateCallback extends BodyExecutionCallback.TailCall {

        private final transient PodTemplate podTemplate;

        private PodTemplateCallback(PodTemplate podTemplate) {
            this.podTemplate = podTemplate;
        }

        @Override
        /**
         * Remove the template after step is done
         */
        protected void finished(StepContext context) throws Exception {
            Cloud cloud = Jenkins.getActiveInstance().getCloud(step.getCloud());
            if (cloud instanceof KubernetesCloud) {
                KubernetesCloud kubernetesCloud = (KubernetesCloud) cloud;
                kubernetesCloud.removeTemplate(podTemplate);
            }
        }
    }
}
