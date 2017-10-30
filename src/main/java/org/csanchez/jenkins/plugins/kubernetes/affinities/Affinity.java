package org.csanchez.jenkins.plugins.kubernetes.affinities;

import hudson.model.AbstractDescribableImpl;

import java.io.Serializable;

public abstract class Affinity extends AbstractDescribableImpl<Affinity> implements Serializable {

    private static final long serialVersionUID = 1L;
}
