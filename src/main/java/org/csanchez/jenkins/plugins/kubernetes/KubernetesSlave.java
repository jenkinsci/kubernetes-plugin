package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.durabletask.executors.Messages;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(KubernetesSlave.class.getName());

    private static final long serialVersionUID = -8642936855413034232L;
    private static final String DEFAULT_AGENT_PREFIX = "jenkins-agent";

    /**
     * The resource bundle reference
     */
    private final static ResourceBundleHolder HOLDER = ResourceBundleHolder.get(Messages.class);

    private final String cloudName;
    private final String namespace;
    private final PodTemplate template;

    public PodTemplate getTemplate() {
        return template;
    }

    @Deprecated
    public KubernetesSlave(PodTemplate template, String nodeDescription, KubernetesCloud cloud, String labelStr)
            throws Descriptor.FormException, IOException {

        this(template, nodeDescription, cloud.name, labelStr, new OnceRetentionStrategy(cloud.getRetentionTimeout()));
    }

    @Deprecated
    public KubernetesSlave(PodTemplate template, String nodeDescription, KubernetesCloud cloud, Label label)
            throws Descriptor.FormException, IOException {
        this(template, nodeDescription, cloud.name, label.toString(), new OnceRetentionStrategy(cloud.getRetentionTimeout())) ;
    }

    @Deprecated
    public KubernetesSlave(PodTemplate template, String nodeDescription, KubernetesCloud cloud, String labelStr,
                           RetentionStrategy rs)
            throws Descriptor.FormException, IOException {
        this(template, nodeDescription, cloud.name, labelStr, rs);
    }

    @DataBoundConstructor
    public KubernetesSlave(PodTemplate template, String nodeDescription, String cloudName, String labelStr,
                           RetentionStrategy rs)
            throws Descriptor.FormException, IOException {

        super(getSlaveName(template),
                nodeDescription,
                template.getRemoteFs(),
                1,
                template.getNodeUsageMode() != null ? template.getNodeUsageMode() : Node.Mode.NORMAL,
                labelStr == null ? null : labelStr,
                new KubernetesLauncher(),
                rs,
                template.getNodeProperties());

        this.cloudName = cloudName;
        this.namespace = Util.fixEmpty(template.getNamespace());
        this.template = template;
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getNamespace() {
        return namespace;
    }

    /**

     * @deprecated Please use the strongly typed getKubernetesCloud() instead.
     */
    @Deprecated
    public Cloud getCloud() {
        return Jenkins.getInstance().getCloud(getCloudName());
    }

    /**
     * Returns the cloud instance which created this slave.
     * @return the cloud instance which created this slave.
     * @throws IllegalStateException if the cloud doesn't exist anymore, or is not a {@link KubernetesCloud}.
     */
    @Nonnull
    public KubernetesCloud getKubernetesCloud() {
        Cloud cloud = Jenkins.getInstance().getCloud(getCloudName());
        if (cloud instanceof KubernetesCloud) {
            return (KubernetesCloud) cloud;
        } else {
            throw new IllegalStateException(getClass().getName() + " can be launched only by instances of " + KubernetesCloud.class.getName());
        }
    }

    static String getSlaveName(PodTemplate template) {
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String name = template.getName();
        if (StringUtils.isEmpty(name)) {
            return String.format("%s-%s", DEFAULT_AGENT_PREFIX,  randString);
        }
        // no spaces
        name = name.replaceAll("[ _]", "-").toLowerCase();
        // keep it under 63 chars (62 is used to account for the '-')
        name = name.substring(0, Math.min(name.length(), 62 - randString.length()));
        return String.format("%s-%s", name, randString);
    }

    @Override
    public KubernetesComputer createComputer() {
        return new KubernetesComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating Kubernetes instance for agent {0}", name);

        Computer computer = toComputer();
        if (computer == null) {
            String msg = String.format("Computer for agent is null: %s", name);
            LOGGER.log(Level.SEVERE, msg);
            listener.fatalError(msg);
            return;
        }

        if (getCloudName() == null) {
            String msg = String.format("Cloud name is not set for agent, can't terminate: %s", name);
            LOGGER.log(Level.SEVERE, msg);
            listener.fatalError(msg);
            computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
            return;
        }
        KubernetesCloud cloud;
        try {
            cloud = getKubernetesCloud();
        } catch (IllegalStateException e) {
            e.printStackTrace(listener.fatalError("Unable to terminate slave. Cloud may have been removed. There may be leftover resources on the Kubernetes cluster."));
            LOGGER.log(Level.SEVERE, String.format("Unable to terminate slave %s. Cloud may have been removed. There may be leftover resources on the Kubernetes cluster.", name));
            computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
            return;
        }
        KubernetesClient client;
        try {
            client = cloud.connect();
        } catch (UnrecoverableKeyException | CertificateEncodingException | NoSuchAlgorithmException
                | KeyStoreException e) {
            String msg = String.format("Failed to connect to cloud %s", getCloudName());
            e.printStackTrace(listener.fatalError(msg));
            computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
            return;
        }

        String actualNamespace = getNamespace() == null ? client.getNamespace() : getNamespace();
        try {
            Boolean deleted = client.pods().inNamespace(actualNamespace).withName(name).delete();
            if (!Boolean.TRUE.equals(deleted)) {
                String msg = String.format("Failed to delete pod for agent %s/%s: not found", actualNamespace, name);
                LOGGER.log(Level.WARNING, msg);
                listener.error(msg);
                return;
            }
        } catch (KubernetesClientException e) {
            String msg = String.format("Failed to delete pod for agent %s/%s: %s", actualNamespace, name,
                    e.getMessage());
            LOGGER.log(Level.WARNING, msg, e);
            listener.error(msg);
            computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
            return;
        }

        String msg = String.format("Terminated Kubernetes instance for agent %s/%s", actualNamespace, name);
        LOGGER.log(Level.INFO, msg);
        listener.getLogger().println(msg);
        computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
        LOGGER.log(Level.INFO, "Disconnected computer {0}", name);
    }

    @Override
    public String toString() {
        return String.format("KubernetesSlave name: %s", name);
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Kubernetes Slave";
        };

        @Override
        public boolean isInstantiable() {
            return false;
        }

    }
}
