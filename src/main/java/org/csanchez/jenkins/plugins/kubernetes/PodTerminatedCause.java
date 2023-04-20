package org.csanchez.jenkins.plugins.kubernetes;

import hudson.slaves.OfflineCause;
import jenkins.model.CauseOfInterruption;

public class PodTerminatedCause extends CauseOfInterruption {

	final String podName;

	final OfflineCause cause;

	public PodTerminatedCause(String podName, OfflineCause cause) {
		this.podName = podName;
		this.cause = cause;
	}

	@Override
	public String getShortDescription() {
		return "Pod " + podName + " was terminated, cause: " + cause;
	}
}
