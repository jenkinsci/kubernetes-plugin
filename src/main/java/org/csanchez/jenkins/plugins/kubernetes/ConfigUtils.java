package org.csanchez.jenkins.plugins.kubernetes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigUtils {
    private static final Logger LOGGER = Logger.getLogger(ConfigUtils.class.getName());
    public static final String KUBERNETES_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    /**
     * Tries to determine the Kubernetes Namespace from the local container
     * filesystem
     *
     * @return the Namespace or null if it could not determine the namespace.
     */
    public static String getSystemNamespace() {
	String namespace = null;
	LOGGER.log(Level.FINE,
		"Trying to configure client namespace from Kubernetes service account namespace path...");
	boolean serviceAccountNamespaceExists = Files.isRegularFile(new File(KUBERNETES_NAMESPACE_PATH).toPath());
	if (serviceAccountNamespaceExists) {
	    LOGGER.log(Level.INFO, "Found service account namespace at: [" + KUBERNETES_NAMESPACE_PATH + "].");
	    try {
		namespace = new String(Files.readAllBytes(new File(KUBERNETES_NAMESPACE_PATH).toPath()));
		namespace = namespace.replace(System.lineSeparator(), "");
	    } catch (IOException e) {
		LOGGER.log(Level.WARNING,
			"Error reading service account namespace from: [" + KUBERNETES_NAMESPACE_PATH + "].", e);
	    }
	} else {
	    LOGGER.log(Level.INFO,
		    "Did not find service account namespace at: [" + KUBERNETES_NAMESPACE_PATH + "]. Ignoring.");
	}
	return namespace;
    }
}
