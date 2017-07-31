package org.csanchez.jenkins.plugins.kubernetes;

import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class KubernetesCloudUtils {

    private static final Logger LOGGER = Logger.getLogger(KubernetesCloudUtils.class.getName());

    private KubernetesCloudUtils() {}

    public static KubernetesCloud getCloud(String cloudName) {
        return (KubernetesCloud) Jenkins.getInstance().getCloud(cloudName);
    }

    public static void checkCloudExistence(Cloud cloud, String expectedCloudName) {
        if (cloud == null) {
            String msg = format("Slave cloud no longer exists: %s", expectedCloudName);
            handleCheckFailure(Level.WARNING, msg);
        }
        if (!(cloud instanceof KubernetesCloud)) {
            String msg = format("Slave cloud %s is not a KubernetesCloud, something is very wrong", expectedCloudName);
            handleCheckFailure(Level.SEVERE, msg);
        }
    }

    private static void handleCheckFailure(Level logLevel, String msg) {
        LOGGER.log(logLevel, msg);
        throw new CloudEntityVerificationException(msg);
    }
}
