package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers result for slots registration and provides an easy way to unregister.
 */
class LimitRegistrationResults {
    private static final Logger LOGGER = Logger.getLogger(LimitRegistrationResults.class.getName());

    private final KubernetesCloud cloud;

    private final List<Result> results = new ArrayList<>();

    public LimitRegistrationResults(@NonNull KubernetesCloud cloud) {
        this.cloud = cloud;
    }

    /**
     * Register a slot for the given pod template and number of executors.
     * @return true is the registration was successful
     */
    public boolean register(@NonNull PodTemplate podTemplate, int numExecutors) {
        var result = new Result(
                KubernetesProvisioningLimits.get().register(cloud, podTemplate, numExecutors),
                podTemplate,
                numExecutors);
        results.add(result);
        return result.success;
    }

    /**
     * Unregister all slots that were registered through this object previously.
     */
    public void unregister() {
        results.forEach(result -> result.unregister(cloud));
    }

    private static class Result {
        final boolean success;
        final int numExecutors;

        @NonNull
        PodTemplate podTemplate;

        Result(boolean success, @NonNull PodTemplate podTemplate, int numExecutors) {
            this.success = success;
            this.podTemplate = podTemplate;
            this.numExecutors = numExecutors;
        }

        void unregister(KubernetesCloud cloud) {
            if (success) {
                LOGGER.log(
                        Level.FINEST,
                        () -> "Registration was successful, unregistering slot for podTemplate " + podTemplate.getName()
                                + " from cloud " + cloud.name + " with " + numExecutors + " executors");
                KubernetesProvisioningLimits.get().unregister(cloud, podTemplate, numExecutors);
            } else {
                LOGGER.log(
                        Level.FINEST,
                        () -> "Registration previously failed, no need to unregister slot for podTemplate "
                                + podTemplate.getName() + " from cloud " + cloud.name + " with " + numExecutors
                                + " executors");
            }
        }
    }
}
