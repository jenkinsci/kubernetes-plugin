package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.Serializable;

/**
 * Context object for PodTemplate during pipeline execution
 */
public class PodTemplateContext implements Serializable {
    private static final long serialVersionUID = 3065143885759619305L;

    private final String namespace;
    private final String name;

    public PodTemplateContext(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }
}
