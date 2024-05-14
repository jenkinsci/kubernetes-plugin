package org.csanchez.jenkins.plugins.kubernetes;

import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateBuilder.LABEL_KUBERNETES_CONTROLLER;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.sanitizeLabel;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Main;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Manages garbage collection of orphaned pods.
 */
public class GarbageCollection extends AbstractDescribableImpl<GarbageCollection> {
    public static final String ANNOTATION_LAST_REFRESH = "kubernetes.jenkins.io/last-refresh";
    private static final Logger LOGGER = Logger.getLogger(GarbageCollection.class.getName());

    public static final int MINIMUM_GC_TIMEOUT = 120;

    private String namespaces;
    private transient Set<String> namespaceSet;
    private int timeout;

    private static Long RECURRENCE_PERIOD = SystemProperties.getLong(
            GarbageCollection.class.getName() + ".recurrencePeriod",
            Main.isUnitTest ? 5 : TimeUnit.MINUTES.toSeconds(1));

    @DataBoundConstructor
    public GarbageCollection() {}

    public String getNamespaces() {
        return namespaces;
    }

    @DataBoundSetter
    public void setNamespaces(String namespaces) {
        this.namespaces = Util.fixEmptyAndTrim(namespaces);
        if (this.namespaces == null) {
            this.namespaceSet = Set.of();
        } else {
            this.namespaceSet = Set.of(this.namespaces.split("\n"));
        }
    }

    public int getTimeout() {
        return timeout;
    }

    protected Object readResolve() {
        if (namespaces != null) {
            setNamespaces(namespaces);
        }
        return this;
    }

    @DataBoundSetter
    public void setTimeout(int timeout) {
        if (Main.isUnitTest) {
            this.timeout = timeout;
        } else {
            this.timeout = Math.max(timeout, MINIMUM_GC_TIMEOUT);
        }
    }

    public Duration getDurationTimeout() {
        return Duration.ofSeconds(timeout);
    }

    @NonNull
    public Set<String> getNamespaceSet() {
        return namespaceSet == null ? Set.of() : namespaceSet;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GarbageCollection that = (GarbageCollection) o;
        return timeout == that.timeout && Objects.equals(namespaces, that.namespaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespaces, timeout);
    }

    @Override
    public String toString() {
        return "GarbageCollection{" + "namespaces='" + namespaces + '\'' + ", timeout=" + timeout + '}';
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<GarbageCollection> {
        @SuppressWarnings("unused") // stapler
        public FormValidation doCheckTimeout(@QueryParameter String value) {
            return FormValidation.validateIntegerInRange(value, MINIMUM_GC_TIMEOUT, Integer.MAX_VALUE);
        }
    }

    /**
     * Annotate pods owned by live Kubernetes agents to help with garbage collection.
     */
    @Extension
    public static final class PeriodicGarbageCollection extends AsyncPeriodicWork {
        public PeriodicGarbageCollection() {
            super("Garbage collection of orphaned Kubernetes pods");
        }

        @Override
        protected void execute(TaskListener listener) throws IOException, InterruptedException {
            annotateLiveAgents(listener);
            garbageCollect();
        }

        private static void annotateLiveAgents(TaskListener listener) {
            Arrays.stream(Jenkins.get().getComputers())
                    .filter(KubernetesComputer.class::isInstance)
                    .map(KubernetesComputer.class::cast)
                    .forEach(kc -> kc.annotateTtl(listener));
        }

        private static void garbageCollect() {
            for (var cloud : Jenkins.get().clouds.getAll(KubernetesCloud.class)) {
                Optional.ofNullable(cloud.getGarbageCollection()).ifPresent(gc -> {
                    try {
                        var client = cloud.connect();
                        var namespaces = new HashSet<String>();
                        namespaces.add(client.getNamespace());
                        namespaces.addAll(gc.getNamespaceSet());
                        for (var ns : namespaces) {
                            client
                                    .pods()
                                    .inNamespace(ns)
                                    // Only look at pods created by this controller
                                    .withLabel(LABEL_KUBERNETES_CONTROLLER, sanitizeLabel(cloud.getJenkinsUrlOrNull()))
                                    .list()
                                    .getItems()
                                    .stream()
                                    .filter(pod -> {
                                        var lastRefresh = pod.getMetadata()
                                                .getAnnotations()
                                                .get(ANNOTATION_LAST_REFRESH);
                                        if (lastRefresh != null) {
                                            try {
                                                var refreshTime = Long.parseLong(lastRefresh);
                                                var now = Instant.now();
                                                LOGGER.log(
                                                        Level.FINE,
                                                        () -> getQualifiedName(pod) + " refresh diff = "
                                                                + (now.toEpochMilli() - refreshTime) + ", timeout is "
                                                                + gc.getDurationTimeout()
                                                                        .toMillis());
                                                return Duration.between(Instant.ofEpochMilli(refreshTime), now)
                                                                .compareTo(gc.getDurationTimeout())
                                                        > 0;
                                            } catch (NumberFormatException e) {
                                                LOGGER.log(
                                                        Level.WARNING,
                                                        e,
                                                        () -> "Unable to parse last refresh for pod "
                                                                + getQualifiedName(pod) + ", ignoring");
                                                return false;
                                            }
                                        } else {
                                            LOGGER.log(
                                                    Level.FINE, () -> "Ignoring legacy pod " + getQualifiedName(pod));
                                            return false;
                                        }
                                    })
                                    .forEach(pod -> {
                                        LOGGER.log(Level.INFO, () -> "Deleting orphan pod " + getQualifiedName(pod));
                                        client.resource(pod).delete();
                                    });
                        }
                    } catch (KubernetesAuthException e) {
                        LOGGER.log(Level.WARNING, "Error authenticating to Kubernetes", e);
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error while getting Kubernetes client", e);
                    }
                });
            }
        }

        private static String getQualifiedName(@NonNull Pod pod) {
            var metadata = pod.getMetadata();
            return metadata.getNamespace() + "/" + metadata.getName();
        }

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.SECONDS.toMillis(RECURRENCE_PERIOD);
        }
    }
}
