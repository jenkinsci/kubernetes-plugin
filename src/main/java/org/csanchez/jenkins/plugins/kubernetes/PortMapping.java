package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import io.fabric8.kubernetes.api.model.ContainerPort;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "Serialization happens exclusively through XStream and not Java Serialization.")
public class PortMapping extends AbstractDescribableImpl<PortMapping> implements Serializable {

    private String name;
    private Integer containerPort;
    private Integer hostPort;

    @DataBoundConstructor
    public PortMapping(String name, Integer containerPort) {
        this.name = name;
        this.containerPort = containerPort;
    }

    public PortMapping(String name, Integer containerPort, Integer hostPort) {
        this.name = name;
        this.containerPort = containerPort;
        this.hostPort = hostPort;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setContainerPort(Integer containerPort) {
        this.containerPort = containerPort;
    }

    public Integer getContainerPort() {
        return containerPort;
    }

    @DataBoundSetter
    public void setHostPort(Integer hostPort) {
        this.hostPort = hostPort;
    }

    public Integer getHostPort() {
        return hostPort;
    }

    public ContainerPort toPort() {
        ContainerPort p = new ContainerPort();
        p.setName(name);
        p.setContainerPort(containerPort);
        if(hostPort != null) {
            p.setHostPort(hostPort);
        }
        return p;
    }

    public String toString() {
        return String.format("%s,%d,%d", name, containerPort, hostPort);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((containerPort == null) ? 0 : containerPort);
        result = prime * result + ((hostPort == null) ? 0 : hostPort);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PortMapping other = (PortMapping) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (containerPort == null) {
            if (other.containerPort != null)
                return false;
        } else if (!containerPort.equals(other.containerPort))
            return false;
        if (hostPort == null) {
            if (other.hostPort != null)
                return false;
        } else if (!hostPort.equals(other.hostPort))
            return false;
        return true;
    }

    @Extension
    @Symbol("portMapping")
    public static class DescriptorImpl extends Descriptor<PortMapping> {
        @Override
        public String getDisplayName() {
            return "Container Exposed Ports";
        }
    }
}
