package org.csanchez.jenkins.plugins.kubernetes;

public interface PodTemplateGroup {

    void replaceTemplate(KubernetesCloud kubernetesCloud, PodTemplate oldTemplate, PodTemplate newTemplate);

    void removeTemplate(KubernetesCloud kubernetesCloud, PodTemplate podTemplate);

}