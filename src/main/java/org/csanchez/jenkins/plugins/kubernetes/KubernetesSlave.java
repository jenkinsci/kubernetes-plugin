package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.durabletask.executors.Messages;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jenkins.model.Jenkins;

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

    public KubernetesSlave(PodTemplate template, String nodeDescription, KubernetesCloud cloud, String labelStr)
            throws Descriptor.FormException, IOException {

        this(template, nodeDescription, cloud, labelStr, new OnceRetentionStrategy(cloud.getRetentionTimeout()));
    }

    @Deprecated
    public KubernetesSlave(PodTemplate template, String nodeDescription, KubernetesCloud cloud, Label label)
            throws Descriptor.FormException, IOException {
        this(template, nodeDescription, cloud, label.toString(), new OnceRetentionStrategy(cloud.getRetentionTimeout())) ;
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
                new JNLPLauncher(),
                rs,
                template.getNodeProperties());

        this.cloudName = cloudName;
        this.namespace = Util.fixEmpty(template.getNamespace());
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getNamespace() {
        return namespace;
    }

    public Cloud getCloud() {
        return Jenkins.getInstance().getCloud(getCloudName());
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

        try {
            KubernetesSlaveUtils.checkSlaveComputer(this);
            KubernetesSlaveUtils.checkSlaveCloudName(this);

            Cloud cloud = getCloud();
            KubernetesCloudUtils.checkCloudExistence(cloud, getCloudName());

            SlaveTerminator slaveTerminator = new SlaveTerminator((KubernetesCloud) cloud);
            if (slaveTerminator.terminatePodSlave(this, namespace)) {
                listener.getLogger().println(format("Terminated Kubernetes pod for slave %s", name));
            } else {
                listener.fatalError(format("Failed to terminate pod for slave %s", name));
            }
        } catch(CloudEntityVerificationException e) {
            listener.fatalError(e.getMessage());
        }
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
