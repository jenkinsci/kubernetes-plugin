package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Queue;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.slaves.AbstractCloudComputer;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.framework.io.ByteBuffer;
import org.kohsuke.stapler.framework.io.LargeText;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carlos Sanchez carlos@apache.org
 */
public class KubernetesComputer extends AbstractCloudComputer<KubernetesSlave> {
    private static final Logger LOGGER = Logger.getLogger(KubernetesComputer.class.getName());

    private boolean launching;

    public KubernetesComputer(KubernetesSlave slave) {
        super(slave);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        Queue.Executable exec = executor.getCurrentExecutable();
        LOGGER.log(Level.FINE, " Computer {0} accepted task {1}", new Object[] {this, exec});
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        Queue.Executable exec = executor.getCurrentExecutable();
        LOGGER.log(Level.FINE, " Computer {0} completed task {1}", new Object[] {this, exec});

        // May take the agent offline and remove it, in which case getNode()
        // above would return null and we'd not find our DockerSlave anymore.
        super.taskCompleted(executor, task, durationMS);
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        Queue.Executable exec = executor.getCurrentExecutable();
        LOGGER.log(Level.FINE, " Computer {0} completed task {1} with problems", new Object[] {this, exec});
    }

    @Exported
    public List<Container> getContainers() throws KubernetesAuthException, IOException {
        if(!Jenkins.get().hasPermission(Computer.EXTENDED_READ)) {
            LOGGER.log(Level.FINE, " Computer {0} getContainers, lack of admin permission, returning empty list", this);
            return Collections.emptyList();
        }

        KubernetesSlave slave = getNode();
        if(slave == null) {
            return Collections.emptyList();
        }

        KubernetesCloud cloud = slave.getKubernetesCloud();
        KubernetesClient client = cloud.connect();

        String namespace = StringUtils.defaultIfBlank(slave.getNamespace(), client.getNamespace());
        Pod pod = client.pods().inNamespace(namespace).withName(getName()).get();

        if (pod == null) {
            return Collections.emptyList();
        }

        return pod.getSpec().getContainers();
    }

    @Exported
    public List<Event> getPodEvents() throws KubernetesAuthException, IOException {
        if(!Jenkins.get().hasPermission(Computer.EXTENDED_READ)) {
            LOGGER.log(Level.FINE, " Computer {0} getPodEvents, lack of admin permission, returning empty list", this);
            return Collections.emptyList();
        }

        KubernetesSlave slave = getNode();
        if(slave != null) {
            KubernetesCloud cloud = slave.getKubernetesCloud();
            KubernetesClient client = cloud.connect();

            String namespace = StringUtils.defaultIfBlank(slave.getNamespace(), client.getNamespace());

            Pod pod = client.pods().inNamespace(namespace).withName(getName()).get();
            if(pod != null) {
                ObjectMeta podMeta = pod.getMetadata();
                String podNamespace = podMeta.getNamespace();

                Map<String, String> fields = new HashMap<>();
                fields.put("involvedObject.uid", podMeta.getUid());
                fields.put("involvedObject.name", podMeta.getName());
                fields.put("involvedObject.namespace", podNamespace);

                EventList eventList = client.v1().events().inNamespace(podNamespace).withFields(fields).list();
                if(eventList != null) {
                    return eventList.getItems();
                }
            }
        }

        return Collections.emptyList();
    }

    public void doContainerLog(@QueryParameter String containerId,
                               StaplerRequest req, StaplerResponse rsp) throws KubernetesAuthException, IOException {
        Jenkins.get().checkPermission(Computer.EXTENDED_READ);

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
        return String.format("KubernetesComputer name: %s agent: %s", getName(), getNode());
    }

    @Override
    public ACL getACL() {
        final ACL base = super.getACL();
        return new ACL() {
            @Override
            public boolean hasPermission(Authentication a, Permission permission) {
                return permission == Computer.CONFIGURE ? false : base.hasPermission(a,permission);
            }
        };
    }

    public void setLaunching(boolean launching) {
        this.launching = launching;
    }

    /**
     *
     * @return true if the Pod has been created in Kubernetes and the current instance is waiting for the pod to be usable.
     */
    public boolean isLaunching() {
        return launching;
    }

    @Override
    public void setAcceptingTasks(boolean acceptingTasks) {
        super.setAcceptingTasks(acceptingTasks);
        if (acceptingTasks) {
            launching = false;
        }
    }
}
