package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;

/**
 * Implementation of {@link PodTemplateFilter} filtering pod templates matching the right label.
 */
@Extension(ordinal = 1000)
public class PodTemplateLabelFilter extends PodTemplateFilter {
    @Override
    protected PodTemplate transform(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate, @CheckForNull Label label) {
        if ((label == null && podTemplate.getNodeUsageMode() == Node.Mode.NORMAL) || (label != null && label.matches(podTemplate.getLabelSet()))) {
            return podTemplate;
        }
        return null;
    }
}
