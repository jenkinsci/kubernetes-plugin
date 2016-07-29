package org.csanchez.jenkins.plugins.kubernetes;

import com.google.common.base.Preconditions;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.ArrayList;
import java.util.List;

public class ContainerTemplate extends AbstractDescribableImpl<ContainerTemplate> {

    private String name;

    private final String image;

    private boolean privileged;

    private boolean alwaysPullImage;

    private String workingDir;

    private String command;

    private String args;

    private int instanceCap;

    private String resourceRequestCpu;

    private String resourceRequestMemory;

    private String resourceLimitCpu;

    private String resourceLimitMemory;

    private final List<ContainerEnvVar> envVars = new ArrayList<ContainerEnvVar>();

    @DataBoundConstructor
    public ContainerTemplate(String image) {
        this(null, image);
    }

    ContainerTemplate(String name, String image) {
        Preconditions.checkArgument(!StringUtils.isBlank(image));
        this.name = name;
        this.image = image;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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

    public String getDisplayName() {
        return "Container Pod Template";
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public void setInstanceCap(int instanceCap) {
        this.instanceCap = instanceCap;
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCapStr(String instanceCapStr) {
        if ("".equals(instanceCapStr)) {
            setInstanceCap(Integer.MAX_VALUE);
        } else {
            setInstanceCap(Integer.parseInt(instanceCapStr));
        }
    }

    public String getInstanceCapStr() {
        if (getInstanceCap() == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
    }

    @DataBoundSetter
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    public boolean isPrivileged() {
        return privileged;
    }

    @DataBoundSetter
    public void setAlwaysPullImage(boolean alwaysPullImage) {
        this.alwaysPullImage = alwaysPullImage;
    }

    public boolean isAlwaysPullImage() {
        return alwaysPullImage;
    }

    public List<ContainerEnvVar> getEnvVars() {
        return envVars;
    }

    @DataBoundSetter
    public void setEnvVars(List<ContainerEnvVar> envVars) {
        this.envVars.addAll(envVars);
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

    @Extension
    public static class DescriptorImpl extends Descriptor<ContainerTemplate> {

        @Override
        public String getDisplayName() {
            return "Container Template";
        }
    }
}
