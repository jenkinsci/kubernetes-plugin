package org.csanchez.jenkins.plugins.kubernetes;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.github.kubernetes.java.client.model.Container;
import com.github.kubernetes.java.client.model.Pod;
import com.github.kubernetes.java.client.model.Port;
import com.google.common.base.Preconditions;
import com.nirima.jenkins.plugins.docker.DockerTemplate;
import com.nirima.jenkins.plugins.docker.utils.RetryingComputerLauncher;

/**
 * {@link hudson.slaves.ComputerLauncher} for Docker that waits for the instance
 * to really come up before proceeding to the real user-specified
 * {@link hudson.slaves.ComputerLauncher}.
 */
public class KubernetesComputerLauncher extends DelegatingComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(KubernetesComputerLauncher.class.getName());

    public KubernetesComputerLauncher(Pod pod, DockerTemplate template) {
        super(makeLauncher(pod, template));
    }

    private static ComputerLauncher makeLauncher(Pod pod, DockerTemplate template) {
        SSHLauncher sshLauncher = getSSHLauncher(pod, template);
        return new RetryingComputerLauncher(sshLauncher);
    }

    private static SSHLauncher getSSHLauncher(Pod pod, DockerTemplate template) {
        Preconditions.checkNotNull(pod);
        Preconditions.checkNotNull(template);

        String host = pod.getCurrentState().getHostIP();
        List<Container> containers = pod.getDesiredState().getManifest().getContainers();
        Container container = containers.get(0);
        List<Port> ports = container.getPorts();
        int port = ports.get(0).getHostPort();

        LOGGER.log(Level.INFO, "Creating slave SSH launcher for " + host + ":" + port);

        StandardUsernameCredentials credentials = SSHLauncher.lookupSystemCredentials(template.credentialsId);

        return new SSHLauncher( //
                host, //
                port, //
                credentials, //
                template.jvmOptions, //
                template.javaPath, //
                template.prefixStartSlaveCmd, //
                template.suffixStartSlaveCmd, //
                60 // template.getSSHLaunchTimeoutMinutes() * 60
        );

    }
}
