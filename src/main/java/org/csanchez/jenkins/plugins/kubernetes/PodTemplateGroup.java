package org.csanchez.jenkins.plugins.kubernetes;

public interface PodTemplateGroup {
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
     * Returns the URL to redirect to after the template is saved.
     * @return the URL to redirect to after the template is saved.
     */
    String redirectUrl();
}
