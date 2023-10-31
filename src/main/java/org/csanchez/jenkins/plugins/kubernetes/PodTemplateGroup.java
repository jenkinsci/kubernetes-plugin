package org.csanchez.jenkins.plugins.kubernetes;

public interface PodTemplateGroup {

    void replaceTemplate(PodTemplate oldTemplate, PodTemplate newTemplate);

    void removeTemplate(PodTemplate podTemplate);

}