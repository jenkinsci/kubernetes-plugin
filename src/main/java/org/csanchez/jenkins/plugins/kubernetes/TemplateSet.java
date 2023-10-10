package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.RootAction;
import jenkins.model.Jenkins;
import jenkins.model.ModelObjectWithChildren;
import jenkins.model.ModelObjectWithContextMenu;
import jenkins.model.ModelObjectWithContextMenu.ContextMenu;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

@Restricted(NoExternalUse.class)
public class TemplateSet extends AbstractModelObject implements Describable<TemplateSet>, ModelObjectWithChildren, RootAction, StaplerProxy{

    @Override
    public String getDisplayName() {
        return "Templates";
    }

    @Override
    public String getSearchUrl() {
        return "/templates";
    }

    @Override
    public String getIconFileName() {
        return "symbol-cloud";
        }

    @Override
    public String getUrlName() {
        return "/templates/";
    }

    @Override
    public Object getTarget() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return this;
    }

    @Override
    public ContextMenu doChildrenContextMenu(StaplerRequest request, StaplerResponse response) throws Exception {
            ModelObjectWithContextMenu.ContextMenu m = new ModelObjectWithContextMenu.ContextMenu();
        Jenkins.get().clouds.stream().forEach(c -> m.add(c));//not sure about this
        return m;
    }

    @Override
    public Descriptor<TemplateSet> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(TemplateSet.class);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<TemplateSet> implements StaplerProxy {

        @Override
        public Object getTarget() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return this;
        }
    }
}
