package org.csanchez.jenkins.plugins.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.csanchez.jenkins.plugins.kubernetes.PodVolumes.PodVolume;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Strings;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;

/**
 * Kubernetes Pod Template
 * 
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class PodTemplate extends AbstractDescribableImpl<PodTemplate> {

    private static final String FALLBACK_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private String name;

    private transient String image;

    private transient boolean privileged;

    private transient boolean alwaysPullImage;

    private transient String command;

    private transient String args;

    private transient String remoteFs;

    private int instanceCap;

    private String label;

    private String serviceAccount;

    private String nodeSelector;

    private transient String resourceRequestCpu;

    private transient String resourceRequestMemory;

    private transient String resourceLimitCpu;

    private transient String resourceLimitMemory;

    private final List<PodVolume> volumes = new ArrayList<PodVolume>();

    private List<ContainerTemplate> containers = new ArrayList<ContainerTemplate>();

    @SuppressWarnings("deprecation")
    private transient final List<PodEnvVar> envVars = new ArrayList<PodEnvVar>();

    private final List<PodAnnotation> annotations = new ArrayList<PodAnnotation>();

    private final List<PodImagePullSecret> imagePullSecrets = new ArrayList<PodImagePullSecret>();

    @DataBoundConstructor
    public PodTemplate() {
    }

    @Deprecated
    public PodTemplate(String image, List<? extends PodVolume> volumes) {
        this(null, image, volumes);
    }

    @Deprecated
    PodTemplate(String name, String image, List<? extends PodVolume> volumes) {
        this(name, volumes, Collections.emptyList());
        if (image != null) {
            getContainers().add(new ContainerTemplate(name, image));
        }
    }

    @Restricted(DoNotUse.class) // testing only
    PodTemplate(String name, List<? extends PodVolume> volumes, List<? extends ContainerTemplate> containers) {
        this.name = name;
        this.volumes.addAll(volumes);
        this.containers.addAll(containers);
    }

    private Optional<ContainerTemplate> getFirstContainer() {
        return Optional.ofNullable(getContainers().isEmpty() ? null : getContainers().get(0));
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Deprecated
    public String getImage() {
        return getFirstContainer().map(ContainerTemplate::getImage).orElse(null);
    }

    @Deprecated
    @DataBoundSetter
    public void setCommand(String command) {
        getFirstContainer().ifPresent((i) -> i.setCommand(command));
    }

    @Deprecated
    public String getCommand() {
        return getFirstContainer().map(ContainerTemplate::getCommand).orElse(null);
    }

    @Deprecated
    @DataBoundSetter
    public void setArgs(String args) {
        getFirstContainer().ifPresent((i) -> i.setArgs(args));
    }

    @Deprecated
    public String getArgs() {
        return getFirstContainer().map(ContainerTemplate::getArgs).orElse(null);
    }

    public String getDisplayName() {
        return "Kubernetes Pod Template";
    }

    @DataBoundSetter
    @Deprecated
    public void setRemoteFs(String remoteFs) {
        getFirstContainer().ifPresent((i) -> i.setWorkingDir(remoteFs));
    }

    @Deprecated
    public String getRemoteFs() {
        return getFirstContainer().map(ContainerTemplate::getWorkingDir).orElse(null);
    }

    public void setInstanceCap(int instanceCap) {
        this.instanceCap = instanceCap;
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCapStr(String instanceCapStr) {
        if ("".equals(instanceCapStr)) {
            setInstanceCap(Integer.MAX_VALUE);
        } else {
            setInstanceCap(Integer.parseInt(instanceCapStr));
        }
    }

    public String getInstanceCapStr() {
        if (getInstanceCap() == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setNodeSelector(String nodeSelector) {
        this.nodeSelector = nodeSelector;
    }

    public String getNodeSelector() {
        return nodeSelector;
    }

    @Deprecated
    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        getFirstContainer().ifPresent((i) -> i.setPrivileged(privileged));
    }

    @Deprecated
    public boolean isPrivileged() {
        return getFirstContainer().map(ContainerTemplate::isPrivileged).orElse(false);
    }

    public String getServiceAccount() {
        return serviceAccount;
    }

    @DataBoundSetter
    public void setServiceAccount(String serviceAccount) {
        this.serviceAccount = Util.fixEmpty(serviceAccount);
    }

    @Deprecated
    @DataBoundSetter
    public void setAlwaysPullImage(boolean alwaysPullImage) {
        getFirstContainer().ifPresent((i) -> i.setAlwaysPullImage(alwaysPullImage));
    }

    @Deprecated
    public boolean isAlwaysPullImage() {
        return getFirstContainer().map(ContainerTemplate::isAlwaysPullImage).orElse(false);
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    public List<PodEnvVar> getEnvVars() {
        return getFirstContainer().map((i) -> PodEnvVar.fromContainerEnvVar(i.getEnvVars()))
                .orElse(Collections.emptyList());
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    @DataBoundSetter
    public void setEnvVars(List<PodEnvVar> envVars) {
        getFirstContainer().ifPresent((i) -> i.setEnvVars(PodEnvVar.asContainerEnvVar(envVars)));
    }

    public List<PodAnnotation> getAnnotations() {
        return annotations;
    }

    @DataBoundSetter
    public void setAnnotations(List<PodAnnotation> annotations) {
        this.annotations.addAll(annotations);
    }

    public List<PodImagePullSecret> getImagePullSecrets() {
        return imagePullSecrets;
    }

    @DataBoundSetter
    public void setImagePullSecrets(List<PodImagePullSecret> imagePullSecrets) {
        this.imagePullSecrets.addAll(imagePullSecrets);
    }

    @Deprecated
    public String getResourceRequestMemory() {
        return getFirstContainer().map(ContainerTemplate::getResourceRequestMemory).orElse(null);
    }

    @Deprecated
    @DataBoundSetter
    public void setResourceRequestMemory(String resourceRequestMemory) {
        getFirstContainer().ifPresent((i) -> i.setResourceRequestMemory(resourceRequestMemory));
    }

    @Deprecated
    public String getResourceLimitCpu() {
        return getFirstContainer().map(ContainerTemplate::getResourceLimitCpu).orElse(null);
    }

    @Deprecated
    @DataBoundSetter
    public void setResourceLimitCpu(String resourceLimitCpu) {
        getFirstContainer().ifPresent((i) -> i.setResourceLimitCpu(resourceLimitCpu));
    }

    @Deprecated
    public String getResourceLimitMemory() {
        return getFirstContainer().map(ContainerTemplate::getResourceLimitMemory).orElse(null);
    }

    @Deprecated
    @DataBoundSetter
    public void setResourceLimitMemory(String resourceLimitMemory) {
        getFirstContainer().ifPresent((i) -> i.setResourceLimitMemory(resourceLimitMemory));
    }

    @Deprecated
    public String getResourceRequestCpu() {
        return getFirstContainer().map(ContainerTemplate::getResourceRequestCpu).orElse(null);
    }

    @Deprecated
    @DataBoundSetter
    public void setResourceRequestCpu(String resourceRequestCpu) {
        getFirstContainer().ifPresent((i) -> i.setResourceRequestCpu(resourceRequestCpu));
    }

    @DataBoundSetter
    public void setVolumes(@Nonnull List<PodVolume> items) {
        synchronized (this.volumes) {
            this.volumes.clear();
            this.volumes.addAll(items);
        }
    }

    @Nonnull
    public List<PodVolume> getVolumes() {
        return volumes;
    }

    @DataBoundSetter
    public void setContainers(@Nonnull List<ContainerTemplate> items) {
        synchronized (this.containers) {
            this.containers.clear();
            this.containers.addAll(items);
        }
    }

    @Nonnull
    public List<ContainerTemplate> getContainers() {
        return containers;
    }

    @SuppressWarnings("deprecation")
    protected Object readResolve() {
        if (containers == null) {
            // upgrading from 0.8
            containers = new ArrayList<ContainerTemplate>();
            ContainerTemplate containerTemplate = new ContainerTemplate(this.name, this.image);
            containerTemplate.setCommand(command);
            containerTemplate.setArgs(Strings.isNullOrEmpty(args) ? FALLBACK_ARGUMENTS : args);
            containerTemplate.setPrivileged(privileged);
            containerTemplate.setAlwaysPullImage(alwaysPullImage);
            containerTemplate.setEnvVars(PodEnvVar.asContainerEnvVar(envVars));
            containerTemplate.setResourceRequestMemory(resourceRequestMemory);
            containerTemplate.setResourceLimitCpu(resourceLimitCpu);
            containerTemplate.setResourceLimitMemory(resourceLimitMemory);
            containerTemplate.setResourceRequestCpu(resourceRequestCpu);
            containerTemplate.setWorkingDir(remoteFs);
            containers.add(containerTemplate);
        }
        return this;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodTemplate> {

        @Override
        public String getDisplayName() {
            return "Kubernetes Pod Template";
        }
    }
}
