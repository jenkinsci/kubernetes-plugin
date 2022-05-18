package org.csanchez.jenkins.plugins.kubernetes;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Job;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import com.cloudbees.hudson.plugins.folder.Folder;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Descriptor.FormException;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * Provides folder level Kubernetes configuration.
 */
public class KubernetesFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

    private static final String PREFIX_USAGE_PERMISSION = "usage-permission-";

    private Set<String> permittedClouds = new HashSet<>();

    /**
     * Constructor.
     */
    @DataBoundConstructor
    public KubernetesFolderProperty() {}

    @DataBoundSetter
    public void setPermittedClouds(Set<String> permittedClouds){
        this.permittedClouds = new HashSet<>(permittedClouds);
    }

    public Set<String> getPermittedClouds() {
        return permittedClouds == null ? Collections.emptySet() : Collections.unmodifiableSet(permittedClouds);
    }

    private static Set<String> getInheritedClouds(ItemGroup parent) {
        Set<String> inheritedPermissions = new HashSet<>();
        collectAllowedClouds(inheritedPermissions, parent);
        return inheritedPermissions;
    }

    /**
     * Called from Jelly.
     * 
     * @return
     */
    @SuppressWarnings("unused") // Used by jelly
    @Restricted(DoNotUse.class) // Used by jelly
    public List<UsagePermission> getEffectivePermissions() {
        Set<String> inheritedClouds = getInheritedClouds(Stapler.getCurrentRequest().findAncestorObject(Folder.class));
        List<UsagePermission> ps = getUsageRestrictedKubernetesClouds().stream()
                                                                       .map(cloud -> new UsagePermission(cloud.name,
                                                                                                         inheritedClouds.contains(cloud.name) || getEffectivePermissions().contains(cloud.name),
                                                                                                         inheritedClouds.contains(cloud.name)))
                                                                       .collect(Collectors.toList());
        // a non-admin User is only allowed to see granted clouds
        if (!userHasAdministerPermission()) {
            ps = ps.stream().filter(UsagePermission::isGranted).collect(Collectors.toList());
        }

        return ps;
    }

    @SuppressWarnings({"rawtypes"})
    public static boolean isAllowed(KubernetesSlave agent, Job job) {
        ItemGroup parent = job.getParent();
        Set<String> allowedClouds = new HashSet<>();

        KubernetesCloud targetCloud = agent.getKubernetesCloud();
        if (targetCloud.isUsageRestricted()) {
            KubernetesFolderProperty.collectAllowedClouds(allowedClouds, parent);
            return allowedClouds.contains(targetCloud.name);
        }
        return true;
    }

    @Override
    public AbstractFolderProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws FormException {
        if (form == null) {
            return null;
        }

        // ignore modifications silently and return the unmodified object if the user
        // does not have the ADMINISTER Permission
        if (!userHasAdministerPermission()) {
            return this;
        }
        // Backwards compatibility: this method was expecting a set of entries PREFIX_USAGE_PERMISSION+cloudName --> true | false
        // Now we're getting a set of permitted cloud names inside permittedClouds entry
        Set<String> formCloudnames = new HashSet<>();
        if (form.has("permittedClouds")) {
            form.names().stream().filter(x -> form.getBoolean(x.toString())).forEach(x -> formCloudnames.add(x.toString().replace(PREFIX_USAGE_PERMISSION, "")));
        } else {
            formCloudnames.addAll(form.getJSONArray("permittedClouds").stream().map(x -> x.toString()).collect(Collectors.toSet()));
        }
        setPermittedClouds(formCloudnames);
        return this;
    }

    /**
     * Recursively collect all allowed clouds from this folder and its parents.
     * 
     * @param allowedClouds
     *            This Set contains all allowed clouds after returning.
     * @param itemGroup
     *            The itemGroup to inspect.
     */
    public static void collectAllowedClouds(Set<String> allowedClouds, ItemGroup<?> itemGroup) {
        if (itemGroup instanceof AbstractFolder) {
            AbstractFolder<?> folder = (AbstractFolder<?>) itemGroup;
            KubernetesFolderProperty kubernetesFolderProperty = folder.getProperties()
                    .get(KubernetesFolderProperty.class);

            if (kubernetesFolderProperty != null) {
                allowedClouds.addAll(kubernetesFolderProperty.getPermittedClouds());
            }

            collectAllowedClouds(allowedClouds, folder.getParent());
        }
    }

    private static List<KubernetesCloud> getUsageRestrictedKubernetesClouds() {
        List<KubernetesCloud> clouds = Jenkins.get().clouds
                .getAll(KubernetesCloud.class)
                .stream()
                .filter(KubernetesCloud::isUsageRestricted)
                .collect(Collectors.toList());
        clouds.sort(Comparator.<Cloud, String>comparing(o -> o.name));
        return clouds;
    }

    public static class UsagePermission {

        private boolean granted;

        private boolean inherited;

        private String name;

        public UsagePermission(String name, boolean granted, boolean inherited) {
            this.name = name;
            this.granted = granted;
            this.inherited = inherited;
        }

        private void setInherited(boolean inherited) {
            this.inherited = inherited;
        }

        public boolean isInherited() {
            return inherited;
        }

        private void setGranted(boolean granted) {
            this.granted = granted;
        }

        @SuppressWarnings("unused") // by stapler/jelly
        public boolean isGranted() {
            return granted;
        }

        private void setName(String name) {
            this.name = name;
        }

        /**
         * Called from Jelly.
         * 
         * @return
         */
        public String getName() {
            return name;
        }

        @SuppressWarnings("unused") // by stapler/jelly
        public boolean isReadonly() {
            return !userHasAdministerPermission() || isInherited();
        }

    }

    private static boolean userHasAdministerPermission() {
        return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

    private static boolean isUsageRestrictedKubernetesCloud(Cloud cloud) {
        if (cloud instanceof KubernetesCloud) {
            return ((KubernetesCloud) cloud).isUsageRestricted();
        }
        return false;
    }

    /**
     * Descriptor class.
     */
    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.KubernetesFolderProperty_displayName();
        }

        public static List<UsagePermission> getEffectivePermissions() {
            Set<String> inheritedClouds = getInheritedClouds(Stapler.getCurrentRequest().findAncestorObject(Folder.class).getParent());
            List<UsagePermission> ps = getUsageRestrictedKubernetesClouds().stream()
                                                                           .map(cloud -> new UsagePermission(cloud.name, inheritedClouds.contains(cloud.name), inheritedClouds.contains(cloud.name)))
                                                                           .collect(Collectors.toList());
            // a non-admin User is only allowed to see granted clouds
            if (!userHasAdministerPermission()) {
                ps = ps.stream().filter(UsagePermission::isGranted).collect(Collectors.toList());
            }

            return ps;
        }
    }

}
