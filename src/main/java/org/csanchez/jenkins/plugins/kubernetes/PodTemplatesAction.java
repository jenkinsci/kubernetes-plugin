package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Action;

public class PodTemplatesAction  implements Action {

    @Override
    public String getIconFileName() {
        return "symbol-terminal";
    }

    @Override
    public String getDisplayName() {
        return "Pod Templates";
    }

    @Override
    public String getUrlName() {
        return "templates";
    }
    
}
