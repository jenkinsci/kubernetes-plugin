package org.csanchez.jenkins.plugins.kubernetes.volumes;

import io.fabric8.kubernetes.api.model.Quantity;
import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

public interface ProvisionedVolume {
    default String getStorageClassNameOrDefault() {
        return getStorageClassName();
    }

    String getStorageClassName();

    default Map<String, Quantity> getResourceMap() {
        return Collections.singletonMap("storage", new Quantity(getRequestsSizeOrDefault()));
    }

    default String getRequestsSizeOrDefault() {
        return StringUtils.defaultString(getRequestsSize(), "10Gi");
    }

    String getRequestsSize();

    default String getAccessModesOrDefault() {
        return StringUtils.defaultString(getAccessModes(), "ReadWriteOnce");
    }

    String getAccessModes();
}
