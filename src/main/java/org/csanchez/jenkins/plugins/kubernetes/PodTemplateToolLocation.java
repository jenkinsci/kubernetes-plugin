package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Saveable;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;

import java.io.Serializable;
import java.util.Collection;

/**
 *  Pod Template Tool Location
 *  This class extends Jenkins DescribableList as implemented in Slave Class. Also implements Serializable interface
 *  for PodTemplate Class.
 *  Using DescribableList is not possible directly in PodTemplate because DescribableList is not Serializable.
 *
 * @author <a href="mailto:aytuncbeken.ab@gmail.com">Aytunc BEKEN</a>
 */
public class PodTemplateToolLocation extends DescribableList<NodeProperty<?>,NodePropertyDescriptor> implements Serializable {

    public PodTemplateToolLocation() {}


    public PodTemplateToolLocation(DescribableList.Owner owner) {
        super(owner);
    }

    public PodTemplateToolLocation(Saveable owner) {
        super(owner);
    }

    public PodTemplateToolLocation(Saveable owner, Collection<? extends NodeProperty<?>> initialList) {
        super(owner,initialList);
    }

}
