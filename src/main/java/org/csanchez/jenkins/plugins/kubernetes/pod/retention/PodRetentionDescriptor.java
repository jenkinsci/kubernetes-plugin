package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import hudson.model.Descriptor;

/**
 * A {@link Descriptor} for any {@link PodRetention} implementation.
 */
public abstract class PodRetentionDescriptor extends Descriptor<PodRetention> {

}
