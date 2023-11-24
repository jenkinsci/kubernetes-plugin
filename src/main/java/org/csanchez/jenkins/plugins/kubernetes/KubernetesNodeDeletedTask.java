package org.csanchez.jenkins.plugins.kubernetes;

import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;

public class KubernetesNodeDeletedTask implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(KubernetesNodeDeletedTask.class.getName());

    private KubernetesSlave node;

    public KubernetesNodeDeletedTask(KubernetesSlave node) {
        this.node = node;
    }

    public void run() {
        PodTemplate template = node.getTemplateOrNull();
        try {
            KubernetesClient client = node.getKubernetesCloud().connect();

            Pod pod =  client.pods().inNamespace(node.getNamespace()).withName(node.getNodeName()).get();
            if (pod != null) {
                long gracePeriod = 30; // TODO: get from pod template or somewhere else.
                Calendar end = Calendar.getInstance();
                end.add(Calendar.SECOND, (int)Math.round(gracePeriod * 1.2));
                while (Calendar.getInstance().before(end)) {
                    if (client.pods().inNamespace(node.getNamespace()).withName(node.getNodeName()).get() == null) {
                        LOGGER.log(Level.INFO, "Pod " + node.getNodeName() + " has been confirmed deleted.");
                        break;
                    }
                    try {
                        LOGGER.log(Level.INFO, "Pod " + node.getNodeName() + " has NOT been confirmed deleted, waiting.");
                        Thread.sleep(3000);
                      } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                    }
                }
            }
        } catch (KubernetesAuthException | KubernetesClientException | IOException e) {
            String msg = String.format("Failed to delete pod for agent %s/%s: %s", node.getNamespace(), node.getNodeName(), e.getMessage());
            LOGGER.log(Level.WARNING, msg, e);
        }

        if (template != null) {
            KubernetesProvisioningLimits instance = KubernetesProvisioningLimits.get();
            instance.unregister(node.getKubernetesCloud(), template, node.getNumExecutors());
        }
    }
}
