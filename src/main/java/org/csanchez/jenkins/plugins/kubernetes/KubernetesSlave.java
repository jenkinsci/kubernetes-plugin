package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import com.github.kubernetes.java.client.model.Pod;
import com.google.common.base.MoreObjects;
import com.nirima.jenkins.plugins.docker.DockerTemplate;

/**
 * @author Carlos Sanchez <carlos@apache.org>
 */
public class KubernetesSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(KubernetesSlave.class.getName());

    private static final long serialVersionUID = -8642936855413034232L;

    public final Pod pod;

    @DataBoundConstructor
    public KubernetesSlave(Pod pod, DockerTemplate template, String nodeDescription) throws Descriptor.FormException,
            IOException {
        super(pod.getId(), nodeDescription, "/", template.getNumExecutors(), Node.Mode.NORMAL, pod.getLabels()
                .getName(), getLauncher(pod), new OnceRetentionStrategy(0), Collections
                .<NodeProperty<Node>> emptyList());

        this.pod = pod;
    }

    private static ComputerLauncher getLauncher(Pod pod) {
        return new JNLPLauncher();
    }

    @Override
    public KubernetesComputer createComputer() {
        return new KubernetesComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating Kubernetes instance for slave " + name);

        try {
            toComputer().disconnect(null);
            // TODO

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failure to terminate instance for slave " + name, e);
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", name).toString();
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
