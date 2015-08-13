package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(KubernetesSlave.class.getName());

    private static final long serialVersionUID = -8642936855413034232L;

    // private final Pod pod;

    private final KubernetesCloud cloud;

    @DataBoundConstructor
    public KubernetesSlave(PodTemplate template, String nodeDescription, KubernetesCloud cloud, Label label)
            throws Descriptor.FormException, IOException {
        super(UUID.randomUUID().toString(),
                nodeDescription,
                template.getRemoteFs(),
                1,
                Node.Mode.NORMAL,
                label == null ? null : label.toString(),
                new JNLPLauncher(),
                ONCE,
                Collections.<NodeProperty<Node>> emptyList());

        // this.pod = pod;
        this.cloud = cloud;
    }

    private final static RetentionStrategy ONCE = new OnceRetentionStrategy(0);

    @Override
    public KubernetesComputer createComputer() {
        return new KubernetesComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating Kubernetes instance for slave {0}", name);

        try {
            cloud.connect().deletePod(name, "jenkins-slave");
            LOGGER.log(Level.INFO, "Terminated Kubernetes instance for slave {0}", name);
            toComputer().disconnect(null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure to terminate instance for slave " + name, e);
        }
    }

    @Override
    public String toString() {
        return String.format("KubernetesSlave name: %n", name);
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
