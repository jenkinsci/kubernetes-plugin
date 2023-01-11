package org.csanchez.jenkins.plugins.kubernetes.pipeline.steps;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

/**
 * Sets up a Kubernetes cloud instance named kubernetes.
 */
public class SetupCloud implements RealJenkinsRule.Step {
    private String hostAddress;
    private Integer agentPort;

    private boolean websocket;

    public SetupCloud(boolean websocket) throws UnknownHostException {
        hostAddress = StringUtils.defaultIfBlank(System.getProperty("jenkins.host.address"), InetAddress.getLocalHost().getHostAddress());
        agentPort = Integer.getInteger("slaveAgentPort");
        this.websocket = websocket;
    }

    public SetupCloud() throws UnknownHostException {
        this(false);
    }

    @Override
    public void run(JenkinsRule r) throws Throwable {
        KubernetesCloud cloud = new KubernetesCloud("kubernetes");
        cloud.setWebSocket(websocket);
        r.jenkins.clouds.add(cloud);
        // Agents running in Kubernetes (minikube) need to connect to this server, so localhost does not work
        URL url = new URL(JenkinsLocationConfiguration.get().getUrl());
        System.err.println("Calling home to address: " + hostAddress);
        URL nonLocalhostUrl = new URL(url.getProtocol(), hostAddress, url.getPort(), url.getFile());
        cloud.setJenkinsUrl(nonLocalhostUrl.toString());
        if (agentPort != null) {
            r.jenkins.setSlaveAgentPort(agentPort);
        }
        r.jenkins.save();
    }
}
