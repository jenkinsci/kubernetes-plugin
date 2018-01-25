package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;

import hudson.BulkChange;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;

/**
 * A persisted map of {@link KubernetesCloud} -&gt; List of {@link PodTemplate} instances. Used to persist {@link PodTemplate} instances created by {@link PodTemplateStepExecution}.
 */
@Extension
public class PodTemplateMap implements Saveable {
    private static final Logger LOGGER = Logger.getLogger(PodTemplateMap.class.getName());

    public static PodTemplateMap get() {
        // TODO Replace with lookupSingleton post 2.87
        return ExtensionList.lookup(PodTemplateMap.class).get(PodTemplateMap.class);
    }

    /**
     * List of Pod Templates indexed by cloud name
     */
    private Map<String, List<PodTemplate>> map;

    public PodTemplateMap() {
        load();
        if (map == null) {
            map = new CopyOnWriteMap.Hash<>();
        }
        check();
    }

    private void check() {
        List<KubernetesCloud> clouds = Jenkins.getInstance().clouds.getAll(KubernetesCloud.class);
        Set<String> names = clouds.stream().map(cloud -> cloud.name).collect(toSet());
        Set<String> cloudNames = new HashSet<>(map.keySet());

        // Remove entries for non-existent clouds
        for (String cloudName : cloudNames) {
           if (!names.contains(cloudName)) {
               map.remove(cloudName);
           }
        }
    }

    /**
     * Returns a read-only view of the templates available for the corresponding cloud instance.
     * @param cloud The kubernetes cloud instance for which templates are needed
     * @return a read-only view of the templates available for the corresponding cloud instance.
     */
    @Nonnull
    public List<PodTemplate> getTemplates(@Nonnull KubernetesCloud cloud) {
        return Collections.unmodifiableList(getOrCreateTemplateList(cloud));
    }

    private List<PodTemplate> getOrCreateTemplateList(@Nonnull KubernetesCloud cloud) {
        List<PodTemplate> podTemplates = map.get(cloud.name);
        return podTemplates == null ? new CopyOnWriteArrayList<>() : podTemplates;
    }

    /**
     * Adds a template for the corresponding cloud instance.
     * @param cloud The cloud instance.
     * @param podTemplate The pod template to add.
     */
    public void addTemplate(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate podTemplate) {
        List<PodTemplate> list = getOrCreateTemplateList(cloud);
        list.add(podTemplate);
        map.put(cloud.name, list);
        save();
    }

    public void removeTemplate(@Nonnull KubernetesCloud cloud, @Nonnull PodTemplate podTemplate) {
        List<PodTemplate> list = map.get(cloud.name);
        if (list != null) {
            if (list.remove(podTemplate)) {
                save();
            }
        }
    }

    /**
     * Saves the configuration info to the disk.
     */
    public synchronized void save() {
        if(BulkChange.contains(this))   return;
        try {
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save "+getConfigFile(),e);
        }
    }

    /**
     * Loads the data from the disk into this object.
     *
     * <p>
     * The constructor of the derived class must call this method.
     * (If we do that in the base class, the derived class won't
     * get a chance to set default values.)
     */
    public synchronized void load() {
        XmlFile file = getConfigFile();
        if(!file.exists())
            return;

        try {
            file.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load "+file, e);
        }
    }

    protected XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(),"kubernetes-podtemplates.xml"));
    }
}
