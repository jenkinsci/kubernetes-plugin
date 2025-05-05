package org.csanchez.jenkins.plugins.kubernetes;

import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

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
     * @return The permission required to manage the templates in this group.
     */
    Permission getManagePermission();

    /**
     * @return {@code true} if the current {@link Authentication} has permissions to add / replace / remove templates.
     */
    default boolean hasManagePermission() {
        return Jenkins.get().hasPermission(getManagePermission());
    }
    /**
     * Checks whether the current {@link Authentication} has sufficient permissions to manage the templates in this group.
     * @throws AccessDeniedException if access is denied for the current {@link Authentication}.
     */
    default void checkManagePermission() throws AccessDeniedException {
        Jenkins.get().checkPermission(getManagePermission());
    }
}
