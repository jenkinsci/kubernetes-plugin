package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplateSource;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.CopyOnWriteMap;

/**
 * A map of {@link KubernetesCloud} -&gt; List of {@link PodTemplate} instances.
 */
@Extension
public class PodTemplateMap {
    private static final Logger LOGGER = Logger.getLogger(PodTemplateMap.class.getName());

    public static PodTemplateMap get() {
        return ExtensionList.lookupSingleton(PodTemplateMap.class);
    }

    /**
     * List of Pod Templates indexed by cloud name
     */
    private Map<String, List<PodTemplate>> map = new CopyOnWriteMap.Hash<>();

    /**
     * Returns a read-only view of the templates available for the corresponding cloud instance.
     * @param cloud The kubernetes cloud instance for which templates are needed
     * @return a read-only view of the templates available for the corresponding cloud instance.
     */
    @NonNull
    public List<PodTemplate> getTemplates(@NonNull KubernetesCloud cloud) {
        return Collections.unmodifiableList(getOrCreateTemplateList(cloud));
    }

    private List<PodTemplate> getOrCreateTemplateList(@NonNull KubernetesCloud cloud) {
        List<PodTemplate> podTemplates = map.get(cloud.name);
        return podTemplates == null ? new CopyOnWriteArrayList<>() : podTemplates;
    }

    /**
     * Adds a template for the corresponding cloud instance.
     * @param cloud The cloud instance.
     * @param podTemplate The pod template to add.
     */
    public void addTemplate(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate) {
        synchronized (this.map) {
            LOGGER.log(Level.FINE, "Registering template with id=" + podTemplate.getId() + " to kubernetes cloud " + cloud.name);
            List<PodTemplate> list = getOrCreateTemplateList(cloud);
            list.add(podTemplate);
            map.put(cloud.name, list);
        }
    }

    public void removeTemplate(@NonNull KubernetesCloud cloud, @NonNull PodTemplate podTemplate) {
        synchronized (this.map) {
            LOGGER.log(Level.FINE, "Unregistering template with id=" + podTemplate.getId() + " from kubernetes cloud " + cloud.name);
            getOrCreateTemplateList(cloud).remove(podTemplate);
        }
    }

    @Extension
    public static class PodTemplateSourceImpl extends PodTemplateSource {

        @NonNull
        @Override
        public List<PodTemplate> getList(@NonNull KubernetesCloud cloud) {
            return PodTemplateMap.get().getTemplates(cloud);
        }
    }

}
