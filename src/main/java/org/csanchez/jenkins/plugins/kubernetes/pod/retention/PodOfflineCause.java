package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import hudson.slaves.OfflineCause;
import org.jvnet.localizer.Localizable;

/**
 * {@link OfflineCause} for Kubernetes Pods.
 */
public class PodOfflineCause extends OfflineCause.SimpleOfflineCause {

    /**
     * Create new pod offline cause.
     * @param description offline description
     */
    protected PodOfflineCause(Localizable description) {
        super(description);
    }

}
