package org.csanchez.jenkins.plugins.kubernetes.volumes;

import hudson.util.ListBoxModel;

public class PVCVolumeUtils {
    public static final ListBoxModel ACCESS_MODES_BOX = new ListBoxModel()
            .add("ReadWriteOnce")
            .add("ReadOnlyMany")
            .add("ReadWriteMany");
}
