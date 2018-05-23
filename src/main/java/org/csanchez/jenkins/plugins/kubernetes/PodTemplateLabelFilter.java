package org.csanchez.jenkins.plugins.kubernetes;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;

/**
 * Implementation of {@link PodTemplateFilter} filtering pod templates matching the right label.
 */
@Extension
public class PodTemplateLabelFilter extends PodTemplateFilter {
    @Override
    protected PodTemplate transform(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate podTemplate, @CheckForNull Label label) {
        if ((label == null && podTemplate.getNodeUsageMode() == Node.Mode.NORMAL) || (label != null && label.matches(podTemplate.getLabelSet()))) {
            return podTemplate;
        }
        return null;
    }
}
