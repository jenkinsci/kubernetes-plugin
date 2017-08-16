package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Computer;
import hudson.model.Slave;
import org.apache.commons.lang.StringUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class KubernetesSlaveUtils {

    private static final Logger LOGGER = Logger.getLogger(KubernetesSlaveUtils.class.getName());

    private KubernetesSlaveUtils() {}

    public static void checkSlaveName(String actualSlaveName, String expectedName) {
        if (!StringUtils.equals(actualSlaveName, expectedName)) {
            String msg = format("Agent name was expected to be %s, but is %s", expectedName, actualSlaveName);
            handleCheckFailure(msg);
        }
    }

    public static void checkSlaveComputer(Slave slave) {
        Computer computer = slave.toComputer();
        if (computer == null) {
            String msg = format("Computer for agent %s is null", slave.getNodeName());
            handleCheckFailure(msg);
        }
    }

    public static void checkSlaveCloudName(KubernetesSlave slave) {
        if (slave.getCloudName() == null) {
            String msg = format("Cloud name is not set for agent %s, can't terminate", slave.getNodeName());
            handleCheckFailure(msg);
        }
    }

    private static void handleCheckFailure(String msg) {
        LOGGER.log(Level.SEVERE, msg);
        throw new CloudEntityVerificationException(msg);
    }
}
