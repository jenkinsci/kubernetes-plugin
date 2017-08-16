package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.annotations.VisibleForTesting;
import hudson.model.Computer;
import hudson.model.Slave;
import hudson.slaves.OfflineCause;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;

import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class SlaveTerminator {

    /**
     * The resource bundle reference
     */
    private final static ResourceBundleHolder HOLDER = ResourceBundleHolder.get(Messages.class);

    private static final Logger LOGGER = Logger.getLogger(SlaveTerminator.class.getName());

    private final KubernetesCloud cloud;

    public SlaveTerminator(KubernetesCloud cloud) {
        this.cloud = cloud;
    }

    boolean terminatePodSlave(Slave slave, String namespace) {
        String slaveName = slave.getNodeName();
        try {
            KubernetesClient client = cloud.connect();
            boolean deleted = deletePod(client, namespace, slaveName);
            if (deleted) {
                Computer computer = getSlaveComputer(slave);
                if (computer != null) {
                    computer.disconnect(OfflineCause.create(new Localizable(HOLDER, "offline")));
                    LOGGER.log(Level.INFO, "Disconnected computer {0}", slaveName);
                }
                LOGGER.log(Level.INFO, "Terminated Kubernetes pod for agent {0}", slaveName);
            }
            return deleted;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, format("Failed to terminate pod for agent %s", slaveName), e);
            return false;
        }
    }

    @VisibleForTesting
    boolean deletePod(KubernetesClient client, String namespace, String podId) {
        LOGGER.log(Level.INFO, "Will try to delete pod {0} in namespace {1} to remove agent", new Object[] { podId, namespace });
        PodResource<Pod, DoneablePod> pods = client.pods().inNamespace(namespace).withName(podId);
        Boolean deletionResult = pods.delete();
        if (deletionResult == null) {
            String msg = format("Pod %s was not found in namespace %s", podId, namespace);
            LOGGER.log(Level.SEVERE, msg);
            throw new KubernetesSlaveException(msg);
        } else if (!deletionResult) {
            LOGGER.log(Level.SEVERE, "Failed to delete pod {0} from namespace {1}", new Object[] { podId, namespace });
            return false;
        }
        return true;
    }

    // Have to have it in a separate method for tests, as toComputer() is final and can't be mocked
    @VisibleForTesting
    Computer getSlaveComputer(Slave slave) {
        return slave.toComputer();
    }
}
