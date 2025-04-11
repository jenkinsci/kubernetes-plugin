package org.csanchez.jenkins.plugins.kubernetes;

import org.springframework.security.access.AccessDeniedException;

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

    /**
     * @return if the current Authentication has permissions to add / replace / remove templates.
     */
    boolean hasManagePermission();

    /**
     * Check that the current Authentication has sufficient permissions.
     * @throws AccessDeniedException if access is denied for the current Authentication
     */
    void checkManagePermission() throws AccessDeniedException;
}
