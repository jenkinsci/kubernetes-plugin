package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Util;
import hudson.util.NamingThreadFactory;
import hudson.model.Descriptor;
import hudson.slaves.NodeProvisioner;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * The default {@link PlannedNodeBuilder} implementation, in case there is other registered.
 */
public class StandardPlannedNodeBuilder extends PlannedNodeBuilder {
    private static final Logger LOGGER = Logger.getLogger(StandardPlannedNodeBuilder.class.getName());
    private static final int THREAD_POOL_SIZE = Integer.parseInt(System.getProperty("org.csanchez.jenkins.plugins.kubernetes.plannedNodeBuilderThreadPoolSize", "100"));
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new NamingThreadFactory(Executors.defaultThreadFactory(), "StandardPlannedNodeBuilderAgent"));

    @Override
    public NodeProvisioner.PlannedNode build() {
        LOGGER.finer("Start build");
        long start = System.currentTimeMillis();
        KubernetesCloud cloud = getCloud();
        PodTemplate t = getTemplate();
        Future f = EXECUTOR_SERVICE.submit(() -> {
            long insideThreadStart = System.currentTimeMillis();
            LOGGER.fine("Creating agent");
            KubernetesSlave agent = KubernetesSlave
                    .builder()
                    .podTemplate(cloud.getUnwrappedTemplate(t))
                    .cloud(cloud)
                    .build();
            LOGGER.fine("Created agent in " + (System.currentTimeMillis() - insideThreadStart) + " milliseconds");
            return agent;
        });
        LOGGER.finer("Created future after " + (System.currentTimeMillis() - start) + " milliseconds");
        NodeProvisioner.PlannedNode result = new NodeProvisioner.PlannedNode(Util.fixNull("Kubernetes Agent"), f, getNumExecutors());
        LOGGER.finer("Exiting build after " + (System.currentTimeMillis() - start) + " milliseconds");
        return result;
    }
}
