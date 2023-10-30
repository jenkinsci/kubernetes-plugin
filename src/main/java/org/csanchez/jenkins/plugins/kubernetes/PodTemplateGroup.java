package org.csanchez.jenkins.plugins.kubernetes;

public interface PodTemplateGroup {

    void replaceTemplate(KubernetesCloud kubernetesCloud, PodTemplate podTemplate);

    void removeTemplate(KubernetesCloud kubernetesCloud, PodTemplateGroup podTemplate);

}