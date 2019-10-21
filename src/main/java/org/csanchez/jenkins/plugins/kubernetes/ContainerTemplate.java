package org.csanchez.jenkins.plugins.kubernetes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.csanchez.jenkins.plugins.kubernetes.model.TemplateEnvVar;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Preconditions;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.QueryParameter;


public class ContainerTemplate extends AbstractDescribableImpl<ContainerTemplate> implements Serializable {

    private static final long serialVersionUID = 4212681620316294146L;

    public static final String DEFAULT_WORKING_DIR = "/home/jenkins/agent";

    private static final String OLD_DEFAULT_WORKING_DIR = "/home/jenkins";

    private String name;

    private String image;

    private boolean privileged;

    private Long runAsUser;
    
    private Long runAsGroup;

    private boolean alwaysPullImage;

    private String workingDir;

    private String command;

    private String args;

    private boolean ttyEnabled;

    private String resourceRequestCpu;

    private String resourceRequestMemory;

    private String resourceLimitCpu;

    private String resourceLimitMemory;
    private String shell;

    private final List<TemplateEnvVar> envVars = new ArrayList<>();
    private List<PortMapping> ports = new ArrayList<PortMapping>();

    private ContainerLivenessProbe livenessProbe;

    @Deprecated
    public ContainerTemplate(String image) {
        this(null, image);
    }

    @DataBoundConstructor
    public ContainerTemplate(String name, String image) {
        Preconditions.checkArgument(!StringUtils.isBlank(image));
        this.name = name;
        this.image = image;
    }

    public ContainerTemplate(String name, String image, String command, String args) {
        Preconditions.checkArgument(!StringUtils.isBlank(image));
        this.name = name;
        this.image = image;
        this.command = command;
        this.args = args;
    }

    public ContainerTemplate(ContainerTemplate from) {
        this.setName(from.getName());
        this.setImage(from.getImage());
        this.setPrivileged(from.isPrivileged());
        this.setRunAsUser(from.getRunAsUser());
        this.setRunAsGroup(from.getRunAsGroup());
        this.setAlwaysPullImage(from.isAlwaysPullImage());
        this.setWorkingDir(from.getWorkingDir());
        this.setCommand(from.getCommand());
        this.setArgs(from.getArgs());
        this.setTtyEnabled(from.isTtyEnabled());
        this.setResourceRequestCpu(from.getResourceRequestCpu());
        this.setResourceRequestMemory(from.getResourceRequestMemory());
        this.setResourceLimitCpu(from.getResourceLimitCpu());
        this.setResourceLimitMemory(from.getResourceLimitMemory());
        this.setShell(from.getShell());
        this.setEnvVars(from.getEnvVars());
        this.setPorts(from.getPorts());
        this.setLivenessProbe(from.getLivenessProbe());
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @DataBoundSetter
    public void setArgs(String args) {
        this.args = args;
    }

    public String getArgs() {
        return args;
    }

    @DataBoundSetter
    public void setTtyEnabled(boolean ttyEnabled) {
        this.ttyEnabled = ttyEnabled;
    }

    public boolean isTtyEnabled() {
        return ttyEnabled;
    }

    public String getDisplayName() {
        return "Container Pod Template";
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.workingDir = Util.fixEmpty(workingDir);
    }

    public String getWorkingDir() {
        return workingDir;
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    public boolean isPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setRunAsUser(Long runAsUser) {
        this.runAsUser = runAsUser;
    }
    
    public Long getRunAsUser() {
        return runAsUser;
    }

    @DataBoundSetter
    public void setRunAsGroup(Long runAsGroup) {
        this.runAsGroup = runAsGroup;
    }
    
    public Long getRunAsGroup() {
        return runAsGroup;
    }
    
    @DataBoundSetter
    public void setAlwaysPullImage(boolean alwaysPullImage) {
        this.alwaysPullImage = alwaysPullImage;
    }

    public boolean isAlwaysPullImage() {
        return alwaysPullImage;
    }

    public List<TemplateEnvVar> getEnvVars() {
        return envVars != null ? envVars : Collections.emptyList();
    }

    @DataBoundSetter
    public void setEnvVars(List<TemplateEnvVar> envVars) {
        this.envVars.addAll(envVars);
    }


    public ContainerLivenessProbe getLivenessProbe() { return livenessProbe; }

    @DataBoundSetter
    public void setLivenessProbe(ContainerLivenessProbe livenessProbe) {
        this.livenessProbe = livenessProbe;
    }

    public List<PortMapping> getPorts() {
        return ports != null ? ports : Collections.emptyList();
    }

    @DataBoundSetter
    public void setPorts(List<PortMapping> ports) {
        this.ports = ports;
    }

    public String getResourceRequestMemory() {
        return resourceRequestMemory;
    }

    @DataBoundSetter
    public void setResourceRequestMemory(String resourceRequestMemory) {
        this.resourceRequestMemory = resourceRequestMemory;
    }

    public String getResourceLimitCpu() {
        return resourceLimitCpu;
    }

    @DataBoundSetter
    public void setResourceLimitCpu(String resourceLimitCpu) {
        this.resourceLimitCpu = resourceLimitCpu;
    }

    public String getResourceLimitMemory() {
        return resourceLimitMemory;
    }

    @DataBoundSetter
    public void setResourceLimitMemory(String resourceLimitMemory) {
        this.resourceLimitMemory = resourceLimitMemory;
    }

    public String getResourceRequestCpu() {
        return resourceRequestCpu;
    }

    @DataBoundSetter
    public void setResourceRequestCpu(String resourceRequestCpu) {
        this.resourceRequestCpu = resourceRequestCpu;
    }

    public String getShell() {
        return shell;
    }

    @DataBoundSetter
    public void setShell(String shell) {
        this.shell = shell;
    }

    public Map<String,Object> getAsArgs() {
        Map<String,Object> argMap = new TreeMap<>();

        argMap.put("name", name);

        if (!StringUtils.isEmpty(shell)) {
            argMap.put("shell", shell);
        }

        return argMap;
    }

    @Extension
    @Symbol("containerTemplate")
    public static class DescriptorImpl extends Descriptor<ContainerTemplate> {

        @Override
        public String getDisplayName() {
            return "Container Template";
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public List<? extends Descriptor> getEnvVarsDescriptors() {
            return DescriptorVisibilityFilter.apply(null, Jenkins.getInstance().getDescriptorList(TemplateEnvVar.class));
        }

        public FormValidation doCheckName(@QueryParameter String value) {
            if(!PodTemplateUtils.validateContainerName(value)) {
                return FormValidation.error(Messages.RFC1123_error(value));
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // jelly
        public String getWorkingDir() {
            return DEFAULT_WORKING_DIR;
        }
    }

    @Override
    public String toString() {
        return "ContainerTemplate{" +
                (name == null ? "" : "name='" + name + '\'') +
                (image == null ? "" : ", image='" + image + '\'') +
                (!privileged ? "" : ", privileged=" + privileged) +
                (runAsUser == null ? "" : ", runAsUser=" + runAsUser) +
                (runAsGroup == null ? "" : ", runAsGroup=" + runAsGroup) +
                (!alwaysPullImage ? "" : ", alwaysPullImage=" + alwaysPullImage) +
                (workingDir == null ? "" : ", workingDir='" + workingDir + '\'') +
                (command == null ? "" : ", command='" + command + '\'') +
                (args == null ? "" : ", args='" + args + '\'') +
                (!ttyEnabled ? "" : ", ttyEnabled=" + ttyEnabled) +
                (resourceRequestCpu == null ? "" : ", resourceRequestCpu='" + resourceRequestCpu + '\'') +
                (resourceRequestMemory == null ? "" : ", resourceRequestMemory='" + resourceRequestMemory + '\'') +
                (resourceLimitCpu == null ? "" : ", resourceLimitCpu='" + resourceLimitCpu + '\'') +
                (resourceLimitMemory == null ? "" : ", resourceLimitMemory='" + resourceLimitMemory + '\'') +
                (envVars == null || envVars.isEmpty() ? "" : ", envVars=" + envVars) +
                (ports == null || ports.isEmpty() ? "" : ", ports=" + ports) +
                (livenessProbe == null ? "" : ", livenessProbe=" + livenessProbe) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ContainerTemplate that = (ContainerTemplate) o;

        if (privileged != that.privileged) {
            return false;
        }
        if (runAsUser != null ? !runAsUser.equals(that.runAsUser) : that.runAsUser != null) {
            return false;
        }
        if (runAsGroup != null ? !runAsGroup.equals(that.runAsGroup) : that.runAsGroup != null) {
            return false;
        }
        if (alwaysPullImage != that.alwaysPullImage) {
            return false;
        }
        if (ttyEnabled != that.ttyEnabled) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (image != null ? !image.equals(that.image) : that.image != null) {
            return false;
        }
        if (workingDir != null ? !workingDir.equals(that.workingDir) : that.workingDir != null) {
            return false;
        }
        if (command != null ? !command.equals(that.command) : that.command != null) {
            return false;
        }
        if (args != null ? !args.equals(that.args) : that.args != null) {
            return false;
        }
        if (resourceRequestCpu != null ? !resourceRequestCpu.equals(that.resourceRequestCpu) : that.resourceRequestCpu != null) {
            return false;
        }
        if (resourceRequestMemory != null ? !resourceRequestMemory.equals(that.resourceRequestMemory) : that.resourceRequestMemory != null) {
            return false;
        }
        if (resourceLimitCpu != null ? !resourceLimitCpu.equals(that.resourceLimitCpu) : that.resourceLimitCpu != null) {
            return false;
        }
        if (resourceLimitMemory != null ? !resourceLimitMemory.equals(that.resourceLimitMemory) : that.resourceLimitMemory != null) {
            return false;
        }
        if (shell != null ? !shell.equals(that.shell) : that.shell != null) {
            return false;
        }
        if (envVars != null ? !envVars.equals(that.envVars) : that.envVars != null) {
            return false;
        }
        if (ports != null ? !ports.equals(that.ports) : that.ports != null) {
            return false;
        }
        return livenessProbe != null ? livenessProbe.equals(that.livenessProbe) : that.livenessProbe == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (image != null ? image.hashCode() : 0);
        result = 31 * result + (privileged ? 1 : 0);
        result = 31 * result + (runAsUser != null ? runAsUser.hashCode() : 0);
        result = 31 * result + (runAsGroup != null ? runAsGroup.hashCode() : 0);
        result = 31 * result + (alwaysPullImage ? 1 : 0);
        result = 31 * result + (workingDir != null ? workingDir.hashCode() : 0);
        result = 31 * result + (command != null ? command.hashCode() : 0);
        result = 31 * result + (args != null ? args.hashCode() : 0);
        result = 31 * result + (ttyEnabled ? 1 : 0);
        result = 31 * result + (resourceRequestCpu != null ? resourceRequestCpu.hashCode() : 0);
        result = 31 * result + (resourceRequestMemory != null ? resourceRequestMemory.hashCode() : 0);
        result = 31 * result + (resourceLimitCpu != null ? resourceLimitCpu.hashCode() : 0);
        result = 31 * result + (resourceLimitMemory != null ? resourceLimitMemory.hashCode() : 0);
        result = 31 * result + (shell != null ? shell.hashCode() : 0);
        result = 31 * result + (envVars != null ? envVars.hashCode() : 0);
        result = 31 * result + (ports != null ? ports.hashCode() : 0);
        result = 31 * result + (livenessProbe != null ? livenessProbe.hashCode() : 0);
        return result;
    }

    private Object readResolve() {
        this.workingDir = Util.fixEmpty(workingDir);
        if (OLD_DEFAULT_WORKING_DIR.equals(workingDir)) {
            this.workingDir = DEFAULT_WORKING_DIR;
        }
        return this;
    }
}
