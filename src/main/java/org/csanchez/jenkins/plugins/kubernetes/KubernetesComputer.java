package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.slaves.AbstractCloudComputer;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.framework.io.LargeText;

import java.io.IOException;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesComputer extends AbstractCloudComputer<KubernetesSlave> {
    private static final Logger LOGGER = Logger.getLogger(KubernetesComputer.class.getName());

    public KubernetesComputer(KubernetesSlave slave) {
        super(slave);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        LOGGER.fine(" Computer " + this + " taskAccepted");
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompleted");

        // May take the agent offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.log(Level.FINE, " Computer " + this + " taskCompletedWithProblems");
    }

    @Exported
    public List<Container> getContainers() throws UnrecoverableKeyException, CertificateEncodingException, NoSuchAlgorithmException, KeyStoreException, IOException, ExecutionException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        KubernetesSlave slave = getNode();
        if(slave == null) {
            return new ArrayList<>();
        }

        KubernetesCloud cloud = slave.getKubernetesCloud();
        KubernetesClient client = cloud.connect();

        String namespace = StringUtils.defaultIfBlank(slave.getNamespace(), client.getNamespace());
        Pod pod = client.pods().inNamespace(namespace).withName(getName()).get();

        return pod.getSpec().getContainers();
    }

    public void doContainerLog(@QueryParameter String containerId,
                               StaplerRequest req, StaplerResponse rsp) throws UnrecoverableKeyException, CertificateEncodingException, NoSuchAlgorithmException, KeyStoreException, IOException, ExecutionException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        ByteBuffer outputStream = new ByteBuffer();
        KubernetesSlave slave = getNode();
        if(slave != null) {
            KubernetesCloud cloud = slave.getKubernetesCloud();
            KubernetesClient client = cloud.connect();

            String namespace = StringUtils.defaultIfBlank(slave.getNamespace(), client.getNamespace());

            client.pods().inNamespace(namespace).withName(getName())
                    .inContainer(containerId).tailingLines(20).watchLog(outputStream);
        }

        new LargeText(outputStream, false).doProgressText(req, rsp);
    }

    @Override
    public String toString() {
        return String.format("KubernetesComputer name: %s slave: %s", getName(), getNode());
    }

}
