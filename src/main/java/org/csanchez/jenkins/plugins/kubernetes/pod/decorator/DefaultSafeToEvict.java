package org.csanchez.jenkins.plugins.kubernetes.pod.decorator;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.HashMap;
import java.util.Map;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

/**
 * Marks agent pods as unsafe for the Kubernetes <a
 * href="https://github.com/kubernetes/autoscaler/tree/master/cluster-autoscaler">cluster-autoscaler</a> to evict.
 *
 * <p>An agent pod is a bare pod that is not backed by a workload controller, so evicting one kills the running build
 * with no rescheduling. This sets {@code cluster-autoscaler.kubernetes.io/safe-to-evict=false} unless the annotation
 * has already been set (via pod template, YAML, or another decorator), leaving the user free to override it. The
 * annotation is inert on clusters that do not run the cluster-autoscaler.
 *
 * @see <a
 *     href="https://github.com/kubernetes/autoscaler/blob/master/cluster-autoscaler/FAQ.md#what-types-of-pods-can-prevent-ca-from-removing-a-node">Cluster
 *     Autoscaler FAQ</a>
 */
@Extension
public class DefaultSafeToEvict implements PodDecorator {

    public static final String SAFE_TO_EVICT_ANNOTATION = "cluster-autoscaler.kubernetes.io/safe-to-evict";

    @NonNull
    @Override
    public Pod decorate(@NonNull KubernetesCloud kubernetesCloud, @NonNull Pod pod) {
        ObjectMeta metadata = pod.getMetadata();
        if (metadata == null) {
            metadata = new ObjectMeta();
            pod.setMetadata(metadata);
        }
        Map<String, String> annotations = metadata.getAnnotations();
        if (annotations == null || !annotations.containsKey(SAFE_TO_EVICT_ANNOTATION)) {
            Map<String, String> updated = annotations == null ? new HashMap<>() : new HashMap<>(annotations);
            updated.put(SAFE_TO_EVICT_ANNOTATION, "false");
            metadata.setAnnotations(updated);
        }
        return pod;
    }
}
