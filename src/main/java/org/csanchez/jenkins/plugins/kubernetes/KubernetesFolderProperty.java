package org.csanchez.jenkins.plugins.kubernetes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;

import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Descriptor.FormException;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Provides folder level Kubernetes configuration.
 */
public class KubernetesFolderProperty extends AbstractFolderProperty<AbstractFolder<?>> {

    private static final Logger LOGGER = Logger.getLogger(KubernetesFolderProperty.class.getName());

    private Set<String> permittedClouds = new HashSet<>();

    /**
     * Constructor.
     */
    @DataBoundConstructor
    public KubernetesFolderProperty() {
    }

    private Set<String> getPermittedClouds() {
        return permittedClouds;
    }

    private Set<String> getInheritedClouds() {
        Set<String> inheritedPermissions = new HashSet<>();
        collectAllowedClouds(inheritedPermissions, getOwner().getParent());

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
        List<UsagePermission> ps = buildPermissions();

        Set<String> inheritedClouds = getInheritedClouds();

        for (UsagePermission p : ps) {
            if (inheritedClouds.contains(p.name)) {
                p.setGranted(true);
                p.setInherited(true);
            }

            if (getPermittedClouds().contains(p.name)) {
                p.setGranted(true);
            }
        }

        // a non-admin User is only allowed to see granted clouds
        if (!userHasAdministerPermission()) {
            ps = ps.stream().filter(UsagePermission::isGranted).collect(Collectors.toList());
        }

        return ps;
    }

    private static List<UsagePermission> buildPermissions() {
        List<UsagePermission> ps = new ArrayList<>();

        for (KubernetesCloud kubernetesCloud : getUsageRestrictedKubernetesClouds()) {
            UsagePermission p = new UsagePermission();
            p.setName(kubernetesCloud.name);
            ps.add(p);
        }

        return ps;
    }

    private static String PREFIX_USAGE_PERMISSION = "usage-permission-";

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

        try {
            Set<String> inheritedGrants = new HashSet<>();
            collectAllowedClouds(inheritedGrants, getOwner().getParent());

            Set<String> permittedClouds = new HashSet<>();
            JSONArray names = form.names();
            if (names != null) {
                for (Object name : names) {
                    String strName = (String) name;

                    if (strName.startsWith(PREFIX_USAGE_PERMISSION) && form.getBoolean(strName)) {
                        String cloud = StringUtils.replaceOnce(strName, PREFIX_USAGE_PERMISSION, "");

                        if (isUsageRestrictedKubernetesCloud(Jenkins.get().getCloud(cloud))
                                && !inheritedGrants.contains(cloud)) {
                            permittedClouds.add(cloud);
                        }
                    }
                }
            }
            this.permittedClouds.clear();
            this.permittedClouds.addAll(permittedClouds);
        } catch (JSONException e) {
            LOGGER.log(Level.SEVERE, e, () -> "reconfigure failed: " + e.getMessage());
        }
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
            return name + (isInherited() ? " (inherited)" : "");
        }

        @SuppressWarnings("unused") // by stapler/jelly
        public String getFormName() {
            return PREFIX_USAGE_PERMISSION + getName();
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

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.KubernetesFolderProperty_displayName();
        }

    }

}
