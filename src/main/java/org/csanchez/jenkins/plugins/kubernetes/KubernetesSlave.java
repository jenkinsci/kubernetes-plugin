package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import com.github.kubernetes.java.client.model.Pod;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.primitives.Ints;
import com.nirima.jenkins.plugins.docker.DockerTemplate;

/**
 * @author Carlos Sanchez <carlos@apache.org>
 */
public class KubernetesSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(KubernetesSlave.class.getName());

    private static final long serialVersionUID = -8642936855413034232L;

    private final Pod pod;

    private final KubernetesCloud cloud;

    @DataBoundConstructor
    public KubernetesSlave(Pod pod, DockerTemplate template, String nodeDescription, KubernetesCloud cloud)
            throws Descriptor.FormException, IOException {
        super(pod.getId(), //
                nodeDescription, //
                "/", //
                template.getNumExecutors(), //
                Node.Mode.NORMAL, //
                pod.getLabels().get("name"), //
                new KubernetesComputerLauncher(pod, template), //
                getRetentionStrategy(template), //
                Collections.<NodeProperty<Node>> emptyList());

        this.pod = pod;
        this.cloud = cloud;
    }

    private static RetentionStrategy getRetentionStrategy(DockerTemplate template) {
        return new OnceRetentionStrategy(Optional.fromNullable(Ints.tryParse(template.idleTerminationMinutes)).or(0));
    }

    @Override
    public KubernetesComputer createComputer() {
        return new KubernetesComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating Kubernetes instance for slave {0}", name);

        try {
            cloud.connect().deletePod(this.pod.getId());
            LOGGER.log(Level.INFO, "Terminated Kubernetes instance for slave {0}", name);
            toComputer().disconnect(null);
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
