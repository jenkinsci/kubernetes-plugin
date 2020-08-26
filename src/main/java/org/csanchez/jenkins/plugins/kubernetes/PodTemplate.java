package org.csanchez.jenkins.plugins.kubernetes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.PodRetention;
import org.csanchez.jenkins.plugins.kubernetes.pod.yaml.YamlMergeStrategy;
import org.csanchez.jenkins.plugins.kubernetes.volumes.PodVolume;
import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import hudson.Extension;
import hudson.Util;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProperty;
import hudson.util.XStream2;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.StringReader;
import jenkins.model.Jenkins;

/**
 * Kubernetes Pod Template
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class PodTemplate extends AbstractDescribableImpl<PodTemplate> implements Serializable, Saveable {

    private static final long serialVersionUID = 3285310269140845583L;

    private static final String FALLBACK_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private static final String DEFAULT_LABEL = "slave-default";

    private static final Logger LOGGER = Logger.getLogger(PodTemplate.class.getName());

    /**
     * Connection timeout expiration in seconds, default to 100 seconds
     */
    public static final Integer DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT = Integer
            .getInteger(PodTemplate.class.getName() + ".connectionTimeout", 100);

    /**
     * Digest function that is used to compute the kubernetes label "jenkins/label-digest"
     */
    public static final HashFunction LABEL_DIGEST_FUNCTION = Hashing.sha1();

    private String inheritFrom;

    private String name;

    private String namespace;

    private String image;

    private boolean privileged;
    
    private Long runAsUser;
    
    private Long runAsGroup;

    private String supplementalGroups;

    private boolean capOnlyOnAlivePods;

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

    private Node.Mode nodeUsageMode = Node.Mode.EXCLUSIVE;

    private String resourceRequestCpu;

    private String resourceRequestMemory;

    private String resourceLimitCpu;

    private String resourceLimitMemory;

    private Boolean hostNetwork;

    private WorkspaceVolume workspaceVolume = WorkspaceVolume.getDefault();

    private final List<PodVolume> volumes = new ArrayList<PodVolume>();

    private List<ContainerTemplate> containers = new ArrayList<ContainerTemplate>();

    private List<TemplateEnvVar> envVars = new ArrayList<>();

    private List<PodAnnotation> annotations = new ArrayList<PodAnnotation>();

    private List<PodImagePullSecret> imagePullSecrets = new ArrayList<PodImagePullSecret>();

    private PodTemplateToolLocation nodeProperties;

    private Long terminationGracePeriodSeconds;

    /**
     * Persisted yaml fragment
     */
    private String yaml;

    /**
     * List of yaml fragments used for transient pod templates. Never persisted
     */
    private transient List<String> yamls;

    public YamlMergeStrategy getYamlMergeStrategy() {
        return yamlMergeStrategy;
    }

    @DataBoundSetter
    public void setYamlMergeStrategy(YamlMergeStrategy yamlMergeStrategy) {
        this.yamlMergeStrategy = yamlMergeStrategy;
    }

    private YamlMergeStrategy yamlMergeStrategy = YamlMergeStrategy.defaultStrategy();

    public Pod getYamlsPod() {
        return yamlMergeStrategy.merge(getYamls());
    }

    private Boolean showRawYaml;

    /**
     * Listener of the run that created this pod template, if applicable
     */
    @CheckForNull
    private transient TaskListener listener;

    @CheckForNull
    private PodRetention podRetention = PodRetention.getPodTemplateDefault();

    @DataBoundConstructor
    public PodTemplate() {
    }

    public PodTemplate(PodTemplate from) {
        XStream2 xs = new XStream2();
        xs.unmarshal(XStream2.getDefaultDriver().createReader(new StringReader(xs.toXML(from))), this);
        this.yamls = from.yamls;
        this.listener = from.listener;
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

    @Deprecated // why would you use this method? It returns the constant "Kubernetes Pod Template".
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
        return getFirstContainer().map(ContainerTemplate::getWorkingDir).orElse(ContainerTemplate.DEFAULT_WORKING_DIR);
    }

    @DataBoundSetter
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

    @DataBoundSetter
    public void setSlaveConnectTimeout(int slaveConnectTimeout) {
        if (slaveConnectTimeout <= 0) {
            LOGGER.log(Level.WARNING, "Agent -> Jenkins connection timeout " +
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

    @DataBoundSetter
    public void setIdleMinutes(int i) {
        this.idleMinutes = i;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
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

    public Map<String, String> getLabelsMap() {
        if (label == null) {
            return ImmutableMap.of(
                    "jenkins/label", DEFAULT_LABEL,
                    "jenkins/label-digest", "0"
            );
        } else {
            return ImmutableMap.of(
                    "jenkins/label", sanitizeLabel(label),
                    "jenkins/label-digest", LABEL_DIGEST_FUNCTION.hashString(label).toString()
            );
        }
    }

    static String sanitizeLabel(String input) {
        String label = input;
        int max = 63;
        // Kubernetes limit
        // a valid label must be an empty string or consist of alphanumeric characters, '-', '_' or '.', and must
        // start and end with an alphanumeric character (e.g. 'MyValue',  or 'my_value',  or '12345', regex used
        // for validation is '(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?')
        if (label.length() > max) {
            label = label.substring(label.length() - max);
        }
        label = label.replaceAll("[^_.a-zA-Z0-9-]", "_").replaceFirst("^[^a-zA-Z0-9]", "x").replaceFirst("[^a-zA-Z0-9]$", "x");
        return label;
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

    @DataBoundSetter
    public void setRunAsUser(String runAsUser) {
        this.runAsUser = PodTemplateUtils.parseLong(runAsUser);
    }
    
    public String getRunAsUser() {
        return runAsUser == null ? null : runAsUser.toString();
    }

    public Long getRunAsUserAsLong() {
        return runAsUser;
    }

    @DataBoundSetter
    public void setRunAsGroup(String runAsGroup) {
        this.runAsGroup = PodTemplateUtils.parseLong(runAsGroup);
    }

    public String getRunAsGroup() {
        return runAsGroup == null ? null : runAsGroup.toString();
    }

    public Long getRunAsGroupAsLong() {
        return runAsGroup;
    }

    @DataBoundSetter
    public void setSupplementalGroups(String supplementalGroups) {
        this.supplementalGroups = Util.fixEmpty(supplementalGroups);
    }

    public String getSupplementalGroups() {
        return this.supplementalGroups;
    }

    @DataBoundSetter
    public void setHostNetwork(Boolean hostNetwork) {
        this.hostNetwork = hostNetwork;
    }

    public boolean isHostNetwork() {
        return isHostNetworkSet()?hostNetwork.booleanValue():false;
    }

    public boolean isHostNetworkSet() {
        return hostNetwork != null;
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

    @DataBoundSetter
    @Deprecated
    public void setCapOnlyOnAlivePods(boolean capOnlyOnAlivePods) {
        this.capOnlyOnAlivePods = capOnlyOnAlivePods;
    }

    @Deprecated
    public boolean isCapOnlyOnAlivePods() {
        return capOnlyOnAlivePods;
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
    public void setNodeProperties(List<? extends NodeProperty<?>> properties)  {
        this.getNodeProperties().clear();
        this.getNodeProperties().addAll(properties);
    }

    @Nonnull
    public PodTemplateToolLocation getNodeProperties(){
        if( this.nodeProperties == null)
            this.nodeProperties = new PodTemplateToolLocation(this);
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

    @Nonnull
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

    /**
     * @return The persisted yaml fragment
     */
    @Restricted(NoExternalUse.class) // Tests and UI
    public String getYaml() {
        return yaml;
    }

    @DataBoundSetter
    public void setYaml(String yaml) {
        this.yaml = Util.fixEmpty(yaml);
    }

    @Nonnull
    public List<String> getYamls() {
        if (yamls == null || yamls.isEmpty()) {
            if (yaml != null) {
                return Collections.singletonList(yaml);
            } else {
                return Collections.emptyList();
            }
        }
        return yamls;
    }

    @VisibleForTesting
    @Restricted(NoExternalUse.class)
    List<String> _getYamls() {
        return yamls;
    }

    public void setYamls(List<String> yamls) {
        if (yamls != null) {
            List<String> ys = new ArrayList<>();
            for (String y : yamls) {
                String trimmed = Util.fixEmpty(y);
                if (trimmed != null) {
                    ys.add(trimmed);
                }
            }
            this.yamls = ys;
        } else {
            this.yamls = Collections.emptyList();
        }
    }

    public PodRetention getPodRetention() {
        return podRetention;
    }

    @DataBoundSetter
    public void setPodRetention(PodRetention podRetention) {
        if (podRetention == null) {
            podRetention = PodRetention.getPodTemplateDefault();
        }
        this.podRetention = podRetention;
    }

    @Nonnull
    public TaskListener getListener() {
        return listener == null ? TaskListener.NULL : listener;
    }

    public void setListener(@CheckForNull TaskListener listener) {
        this.listener = listener;
    }

    public Long getTerminationGracePeriodSeconds() {
        return terminationGracePeriodSeconds;
    }

    public void setTerminationGracePeriodSeconds(Long terminationGracePeriodSeconds) {
        this.terminationGracePeriodSeconds = terminationGracePeriodSeconds;
    }

    protected Object readResolve() {
        if (containers == null) {
            // upgrading from 0.8
            containers = new ArrayList<>();
            ContainerTemplate containerTemplate = new ContainerTemplate(KubernetesCloud.JNLP_NAME, this.image);
            containerTemplate.setCommand(command);
            containerTemplate.setArgs(Strings.isNullOrEmpty(args) ? FALLBACK_ARGUMENTS : args);
            containerTemplate.setPrivileged(privileged);
            containerTemplate.setRunAsUser(getRunAsUser());
            containerTemplate.setRunAsGroup(getRunAsGroup());
            containerTemplate.setAlwaysPullImage(alwaysPullImage);
            containerTemplate.setEnvVars(envVars);
            containerTemplate.setResourceRequestMemory(resourceRequestMemory);
            containerTemplate.setResourceLimitCpu(resourceLimitCpu);
            containerTemplate.setResourceLimitMemory(resourceLimitMemory);
            containerTemplate.setResourceRequestCpu(resourceRequestCpu);
            containerTemplate.setWorkingDir(remoteFs);
            containers.add(containerTemplate);
        }
        
        if (podRetention == null) {
            // https://issues.jenkins-ci.org/browse/JENKINS-53260
            // various legacy paths for injecting pod templates can 
            // bypass the defaulting paths and the
            // value can still be null, so check for it here so 
            // as to not blow up things like termination path
            podRetention = PodRetention.getPodTemplateDefault();
        }

        if (workspaceVolume == null) {
            workspaceVolume = WorkspaceVolume.getDefault();
        }

        if (annotations == null) {
            annotations = new ArrayList<>();
        }

        // Sanitize empty values
        yaml = Util.fixEmpty(yaml);
        if (yamls != null) {
            // JENKINS-57116 Sanitize empty values
            setYamls(yamls);
            // Migration from storage in yamls field
            if (!yamls.isEmpty()) {
                if (yamls.size() > 1) {
                    LOGGER.log(Level.WARNING, "Found several persisted YAML fragments in pod template " + name + ". Only the first fragment will be considered, others will be ignored.");
                }
                yaml = yamls.get(0);
            }
            yamls = null;
        }

        if (yamlMergeStrategy == null) {
            yamlMergeStrategy = YamlMergeStrategy.defaultStrategy();
        }

        return this;
    }

    @Deprecated
    public Pod build(KubernetesClient client, KubernetesSlave slave) {
        return build(slave);
    }

    /**
     * Build a Pod object from a PodTemplate
     *
     * @param slave
     */
    public Pod build(KubernetesSlave slave) {
        return new PodTemplateBuilder(this).withSlave(slave).build();
    }

    /**
     * @deprecated Use {@code Serialization.asYaml(build(KubernetesSlave))} instead.
     */
    @Deprecated
    public String getDescriptionForLogging() {
        return String.format("Agent specification [%s] (%s): %n%s",
                getName(),
                getLabel(),
                getContainersDescriptionForLogging());
    }

    boolean isShowRawYamlSet() {
        return showRawYaml != null;
    }

    public boolean isShowRawYaml() {
        return isShowRawYamlSet() ? showRawYaml.booleanValue() : true;
    }

    @DataBoundSetter
    public void setShowRawYaml(boolean showRawYaml) {
        this.showRawYaml = Boolean.valueOf(showRawYaml);
    }

    private String getContainersDescriptionForLogging() {
        List<ContainerTemplate> containers = getContainers();
        StringBuilder sb = new StringBuilder();
        for (ContainerTemplate ct : containers) {
            sb.append("* [").append(ct.getName()).append("] ").append(ct.getImage());
            StringBuilder optional = new StringBuilder();
            optionalField(optional, "resourceRequestCpu", ct.getResourceRequestCpu());
            optionalField(optional, "resourceRequestMemory", ct.getResourceRequestMemory());
            optionalField(optional, "resourceLimitCpu", ct.getResourceLimitCpu());
            optionalField(optional, "resourceLimitMemory", ct.getResourceLimitMemory());
            if (optional.length() > 0) {
                sb.append("(").append(optional).append(")");
            }
            sb.append("\n");
        }
        if (isShowRawYaml()) {
            for (String yaml : getYamls()) {
                sb.append("yaml:\n")
                    .append(yaml)
                    .append("\n");
            }
        }
        return sb.toString();
    }

    private void optionalField(StringBuilder builder, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(label).append(": ").append(value);
        }
    }

    /**
     * Empty implementation of Saveable interface. This interface is used for DescribableList implementation
     */
    @Override
    public void save()  { }

    @Extension
    public static class DescriptorImpl extends Descriptor<PodTemplate> {

        @Override
        public String getDisplayName() {
            return "Kubernetes Pod Template";
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public List<? extends Descriptor> getEnvVarsDescriptors() {
            return DescriptorVisibilityFilter.apply(null, Jenkins.getInstance().getDescriptorList(TemplateEnvVar.class));
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public WorkspaceVolume getDefaultWorkspaceVolume() {
            return WorkspaceVolume.getDefault();
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public Descriptor getDefaultPodRetention() {
            return Jenkins.get().getDescriptor(PodRetention.getPodTemplateDefault().getClass());
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public YamlMergeStrategy getDefaultYamlMergeStrategy() {
            return YamlMergeStrategy.defaultStrategy();
        }
    }

    @Override
    public String toString() {
        return "PodTemplate{" +
                (inheritFrom == null ? "" : "inheritFrom='" + inheritFrom + '\'') +
                (name == null ? "" : ", name='" + name + '\'') +
                (namespace == null ? "" : ", namespace='" + namespace + '\'') +
                (image == null ? "" : ", image='" + image + '\'') +
                (!privileged ? "" : ", privileged=" + privileged) +
                (runAsUser == null ? "" : ", runAsUser=" + runAsUser) +
                (runAsGroup == null ? "" : ", runAsGroup=" + runAsGroup) +
                (!isHostNetworkSet() ? "" : ", hostNetwork=" + hostNetwork) +
                (!alwaysPullImage ? "" : ", alwaysPullImage=" + alwaysPullImage) +
                (command == null ? "" : ", command='" + command + '\'') +
                (args == null ? "" : ", args='" + args + '\'') +
                (remoteFs == null ? "" : ", remoteFs='" + remoteFs + '\'') +
                (instanceCap == Integer.MAX_VALUE ? "" : ", instanceCap=" + instanceCap) +
                (slaveConnectTimeout == DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT ? "" : ", slaveConnectTimeout=" + slaveConnectTimeout) +
                (idleMinutes == 0 ? "" : ", idleMinutes=" + idleMinutes) +
                (activeDeadlineSeconds == 0 ? "" : ", activeDeadlineSeconds=" + activeDeadlineSeconds) +
                (label == null ? "" : ", label='" + label + '\'') +
                (serviceAccount == null ? "" : ", serviceAccount='" + serviceAccount + '\'') +
                (nodeSelector == null ? "" : ", nodeSelector='" + nodeSelector + '\'') +
                (nodeUsageMode == null ? "" : ", nodeUsageMode=" + nodeUsageMode) +
                (resourceRequestCpu == null ? "" : ", resourceRequestCpu='" + resourceRequestCpu + '\'') +
                (resourceRequestMemory == null ? "" : ", resourceRequestMemory='" + resourceRequestMemory + '\'') +
                (resourceLimitCpu == null ? "" : ", resourceLimitCpu='" + resourceLimitCpu + '\'') +
                (resourceLimitMemory == null ? "" : ", resourceLimitMemory='" + resourceLimitMemory + '\'') +
                ", workspaceVolume=" + workspaceVolume +
                (volumes == null || volumes.isEmpty() ? "" : ", volumes=" + volumes) +
                (containers == null || containers.isEmpty() ? "" : ", containers=" + containers) +
                (envVars == null || envVars.isEmpty() ? "" : ", envVars=" + envVars) +
                (annotations == null || annotations.isEmpty() ? "" : ", annotations=" + annotations) +
                (imagePullSecrets == null || imagePullSecrets.isEmpty() ? "" : ", imagePullSecrets=" + imagePullSecrets) +
                (nodeProperties == null || nodeProperties.isEmpty() ? "" : ", nodeProperties=" + nodeProperties) +
                (yamls == null || yamls.isEmpty() ? "" : ", yamls=" + yamls) +
                '}';
    }
}
