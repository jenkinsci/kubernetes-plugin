/*
Copyright (c) 2018 Aytunc Beken

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions
of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
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


    private static final long serialVersionUID = 42L;

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
