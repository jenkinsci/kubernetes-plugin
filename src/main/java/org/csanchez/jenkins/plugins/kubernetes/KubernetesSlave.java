package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.slaves.*;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;

/**
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(KubernetesSlave.class.getName());

    private static final long serialVersionUID = -8642936855413034232L;

    /**
     * The resource bundle reference
     */
    private final static ResourceBundleHolder HOLDER = ResourceBundleHolder.get(Messages.class);

    // private final Pod pod;

    private transient final KubernetesCloud cloud;

    @DataBoundConstructor
    public KubernetesSlave(PodTemplate template, String nodeDescription, KubernetesCloud cloud, Label label)
            throws Descriptor.FormException, IOException {
        super(getSlaveName(template),
                nodeDescription,
                template.getRemoteFs(),
                1,
                Node.Mode.NORMAL,
                label == null ? null : label.toString(),
                new JNLPLauncher(),
                new CloudRetentionStrategy(cloud.getRetentionTimeout()),
                Collections.<NodeProperty<Node>> emptyList());

        // this.pod = pod;
        this.cloud = cloud;
    }

    static String getSlaveName(PodTemplate template) {
        String hex = Long.toHexString(System.nanoTime());
        String name = template.getName();
        if (StringUtils.isEmpty(name)) {
            return hex;
        }
        // no spaces
        name = template.getName().replace(" ", "-").toLowerCase();
        // keep it under 256 chars
        name = name.substring(0, Math.min(name.length(), 256 - hex.length()));
        return String.format("%s-%s", name, hex);
    }

    @Override
    public KubernetesComputer createComputer() {
        return new KubernetesComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating Kubernetes instance for slave {0}", name);

        Computer computer = toComputer();
        if (computer == null) {
            LOGGER.log(Level.SEVERE, "Computer for slave is null: {0}", name);
            return;
        }

        try {
            cloud.connect().pods().inNamespace(cloud.getNamespace()).withName(name).delete();
            LOGGER.log(Level.INFO, "Terminated Kubernetes instance for slave {0}", name);
            computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
            LOGGER.log(Level.INFO, "Disconnected computer {0}", name);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure to terminate instance for slave " + name, e);
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
