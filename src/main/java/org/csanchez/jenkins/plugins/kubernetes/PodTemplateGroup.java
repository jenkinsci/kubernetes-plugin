package org.csanchez.jenkins.plugins.kubernetes;
/**
 * A group of pod templates that can be saved together.
 */
public interface PodTemplateGroup {

    /**
     * Add the template to the group.
     * @param podTemplate the template to add
     */
    void addTemplate(PodTemplate podTemplate);

    /**
     * Replaces the old template with the new template.
     * @param oldTemplate the old template to replace
     * @param newTemplate the new template to replace with
     */
    void replaceTemplate(PodTemplate oldTemplate, PodTemplate newTemplate);
    /**
     * Removes the template from the group.
     * @param podTemplate the template to remove
     */
    void removeTemplate(PodTemplate podTemplate);
    /**
     * @return the URL to redirect to after the template is saved.
     */
    String getPodTemplateGroupUrl();
}
