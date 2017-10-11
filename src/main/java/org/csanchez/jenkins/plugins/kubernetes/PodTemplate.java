package org.csanchez.jenkins.plugins.kubernetes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Strings;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.tools.ToolLocationNodeProperty;

/**
 * Kubernetes Pod Template
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class PodTemplate extends AbstractDescribableImpl<PodTemplate> implements Serializable {

    private static final long serialVersionUID = 3285310269140845583L;

    private static final String FALLBACK_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private static final Logger LOGGER = Logger.getLogger(PodTemplate.class.getName());

    private static final int DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT = 100;

    private String inheritFrom;

    private String name;

    private String namespace;

    private String image;

    private boolean privileged;

    private boolean alwaysPullImage;

    private String command;

    private String args;

    private String remoteFs;

    private int instanceCap = Integer.MAX_VALUE;

    private int slaveConnectTimeout = DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;

    private int idleMinutes;

    private int activeDeadlineSeconds;

    private String label;

    private String serviceAccount;

    private String nodeSelector;

    private Node.Mode nodeUsageMode;

    private String resourceRequestCpu;

    private String resourceRequestMemory;

    private String resourceLimitCpu;

    private String resourceLimitMemory;

    private boolean customWorkspaceVolumeEnabled;
    private WorkspaceVolume workspaceVolume;

    private final List<PodVolume> volumes = new ArrayList<PodVolume>();

    private List<ContainerTemplate> containers = new ArrayList<ContainerTemplate>();

    private List<TemplateEnvVar> envVars = new ArrayList<>();

    private List<PodAnnotation> annotations = new ArrayList<PodAnnotation>();

    private List<PodImagePullSecret> imagePullSecrets = new ArrayList<PodImagePullSecret>();

    private transient List<ToolLocationNodeProperty> nodeProperties;

    @DataBoundConstructor
    public PodTemplate() {
    }

    public PodTemplate(PodTemplate from) {
        this.setAnnotations(from.getAnnotations());
        this.setContainers(from.getContainers());
        this.setImagePullSecrets(from.getImagePullSecrets());
        this.setInstanceCap(from.getInstanceCap());
        this.setLabel(from.getLabel());
        this.setName(from.getName());
        this.setNamespace(from.getNamespace());
        this.setInheritFrom(from.getInheritFrom());
        this.setNodeSelector(from.getNodeSelector());
        this.setNodeUsageMode(from.getNodeUsageMode());
        this.setServiceAccount(from.getServiceAccount());
        this.setSlaveConnectTimeout(from.getSlaveConnectTimeout());
        this.setActiveDeadlineSeconds(from.getActiveDeadlineSeconds());
        this.setVolumes(from.getVolumes());
        this.setWorkspaceVolume(from.getWorkspaceVolume());
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

    @Restricted(NoExternalUse.class) // testing only
    PodTemplate(String name, List<? extends PodVolume> volumes, List<? extends ContainerTemplate> containers) {
        this.name = name;
        this.volumes.addAll(volumes);
        this.containers.addAll(containers);
    }

    private Optional<ContainerTemplate> getFirstContainer() {
        return Optional.ofNullable(getContainers().isEmpty() ? null : getContainers().get(0));
    }

    public String getInheritFrom() {
        return inheritFrom;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    @DataBoundSetter
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
        if (instanceCap < 0) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = instanceCap;
        }
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    public void setSlaveConnectTimeout(int slaveConnectTimeout) {
        if (slaveConnectTimeout <= 0) {
            LOGGER.log(Level.WARNING, "Slave -> Jenkins connection timeout " +
                    "cannot be <= 0. Falling back to the default value: " +
                    DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT);
            this.slaveConnectTimeout = DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;
        } else {
            this.slaveConnectTimeout = slaveConnectTimeout;
        }
    }

    public int getSlaveConnectTimeout() {
        if (slaveConnectTimeout == 0)
            return DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;
        return slaveConnectTimeout;
    }

    @DataBoundSetter
    public void setInstanceCapStr(String instanceCapStr) {
        if (StringUtils.isBlank(instanceCapStr)) {
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

    @DataBoundSetter
    public void setSlaveConnectTimeoutStr(String slaveConnectTimeoutStr) {
        if (StringUtils.isBlank(slaveConnectTimeoutStr)) {
            setSlaveConnectTimeout(DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT);
        } else {
            setSlaveConnectTimeout(Integer.parseInt(slaveConnectTimeoutStr));
        }
    }

    public String getSlaveConnectTimeoutStr() {
        return String.valueOf(slaveConnectTimeout);
    }

    public void setIdleMinutes(int i) {
        this.idleMinutes = i;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    public void setActiveDeadlineSeconds(int i) {
        this.activeDeadlineSeconds = i;
    }

    public int getActiveDeadlineSeconds() {
        return activeDeadlineSeconds;
    }

    @DataBoundSetter
    public void setIdleMinutesStr(String idleMinutes) {
        if (StringUtils.isBlank(idleMinutes)) {
            setIdleMinutes(0);
        } else {
            setIdleMinutes(Integer.parseInt(idleMinutes));
        }
    }

    public String getIdleMinutesStr() {
        if (getIdleMinutes() == 0) {
            return "";
        } else {
            return String.valueOf(idleMinutes);
        }
    }

    @DataBoundSetter
    public void setActiveDeadlineSecondsStr(String activeDeadlineSeconds) {
        if (StringUtils.isBlank(activeDeadlineSeconds)) {
            setActiveDeadlineSeconds(0);
        } else {
            setActiveDeadlineSeconds(Integer.parseInt(activeDeadlineSeconds));
        }
    }

    public String getActiveDeadlineSecondsStr() {
        if (getActiveDeadlineSeconds() == 0) {
            return "";
        } else {
            return String.valueOf(activeDeadlineSeconds);
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

    @DataBoundSetter
    public void setNodeUsageMode(Node.Mode nodeUsageMode) {
        this.nodeUsageMode = nodeUsageMode;
    }

    @DataBoundSetter
    public void setNodeUsageMode(String nodeUsageMode) {
        this.nodeUsageMode = Node.Mode.valueOf(nodeUsageMode);
    }

    public Node.Mode getNodeUsageMode() {
        return nodeUsageMode;
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

    public List<TemplateEnvVar> getEnvVars() {
        if (envVars == null) {
            return Collections.emptyList();
        }
        return envVars;
    }

    public void addEnvVars(List<TemplateEnvVar> envVars) {
        if (envVars != null) {
            this.envVars.addAll(envVars);
        }
    }

    @DataBoundSetter
    public void setEnvVars(List<TemplateEnvVar> envVars) {
        if (envVars != null) {
            this.envVars.clear();
            this.addEnvVars(envVars);
        }
    }

    public List<PodAnnotation> getAnnotations() {
        if (annotations == null) {
            return Collections.emptyList();
        }
        return annotations;
    }

    public void addAnnotations(List<PodAnnotation> annotations) {
        this.annotations.addAll(annotations);
    }


    @DataBoundSetter
    public void setAnnotations(List<PodAnnotation> annotations) {
        if (annotations != null) {
            this.annotations = new ArrayList<PodAnnotation>();
            this.addAnnotations(annotations);
        }
    }

    public List<PodImagePullSecret> getImagePullSecrets() {
        return imagePullSecrets == null ? Collections.emptyList() : imagePullSecrets;
    }

    public void addImagePullSecrets(List<PodImagePullSecret> imagePullSecrets) {
        this.imagePullSecrets.addAll(imagePullSecrets);
    }

    @DataBoundSetter
    public void setImagePullSecrets(List<PodImagePullSecret> imagePullSecrets) {
        if (imagePullSecrets != null) {
            this.imagePullSecrets.clear();
            this.addImagePullSecrets(imagePullSecrets);
        }
    }

    @DataBoundSetter
    public void setNodeProperties(List<ToolLocationNodeProperty> nodeProperties){
        this.nodeProperties = nodeProperties;
    }

    @Nonnull
    public List<ToolLocationNodeProperty> getNodeProperties(){
        if (nodeProperties == null) {
            return Collections.emptyList();
        }
        return nodeProperties;
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
        if (volumes == null) {
            return Collections.emptyList();
        }
        return volumes;
    }

    public boolean isCustomWorkspaceVolumeEnabled() {
        return customWorkspaceVolumeEnabled;
    }

    @DataBoundSetter
    public void setCustomWorkspaceVolumeEnabled(boolean customWorkspaceVolumeEnabled) {
        this.customWorkspaceVolumeEnabled = customWorkspaceVolumeEnabled;
    }

    public WorkspaceVolume getWorkspaceVolume() {
        return workspaceVolume;
    }

    @DataBoundSetter
    public void setWorkspaceVolume(WorkspaceVolume workspaceVolume) {
        this.workspaceVolume = workspaceVolume;
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
        if (containers == null) {
            return Collections.emptyList();
        }
        return containers;
    }

    @SuppressWarnings("deprecation")
    protected Object readResolve() {
        if (containers == null) {
            // upgrading from 0.8
            containers = new ArrayList<>();
            ContainerTemplate containerTemplate = new ContainerTemplate(KubernetesCloud.JNLP_NAME, this.image);
            containerTemplate.setCommand(command);
            containerTemplate.setArgs(Strings.isNullOrEmpty(args) ? FALLBACK_ARGUMENTS : args);
            containerTemplate.setPrivileged(privileged);
            containerTemplate.setAlwaysPullImage(alwaysPullImage);
            containerTemplate.setEnvVars(envVars);
            containerTemplate.setResourceRequestMemory(resourceRequestMemory);
            containerTemplate.setResourceLimitCpu(resourceLimitCpu);
            containerTemplate.setResourceLimitMemory(resourceLimitMemory);
            containerTemplate.setResourceRequestCpu(resourceRequestCpu);
            containerTemplate.setWorkingDir(remoteFs);
            containers.add(containerTemplate);
        }

        if (annotations == null) {
            annotations = new ArrayList<>();
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
