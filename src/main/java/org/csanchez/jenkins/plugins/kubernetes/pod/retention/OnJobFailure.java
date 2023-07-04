package org.csanchez.jenkins.plugins.kubernetes.pod.retention;

import hudson.Extension;
import hudson.model.*;
import io.fabric8.kubernetes.api.model.Pod;
import jenkins.model.Jenkins;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;


import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.stream.Collectors;

/**
 * This pod retention policy keeps the pod from being terminated if the Jenkins
 * job it's associated with fails.
 *
 * In case of any other result, including errors in determining the result, it
 * will default to deleting the pod.
 */
public class OnJobFailure extends PodRetention implements Serializable {

    private static final long serialVersionUID = -6422177946264212816L;

    private static final Logger LOGGER = Logger.getLogger(OnJobFailure.class.getName());

    private static final String MODULENAME = "OnJobFailure";

    // small convenience function
    private void LOG(Level level, String message) {
        LOGGER.log(level, () -> MODULENAME + ": " + message);
    }

    @DataBoundConstructor
    public OnJobFailure() {
    }

    @Override
    public boolean shouldDeletePod(KubernetesCloud cloud, Pod pod) {
        if (cloud == null || pod == null) {
            LOG(Level.INFO, "shouldDeletePod called without actual cloud and pod");
            return true;
        }

        // Get the current Jenkins instance to access a list of all jobs
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            LOG(Level.INFO, "Couldn't get the current Jenkins reference");
            return true;
        }

        // All known jobs of the current Jenkins instance
        List<Job> jobs = jenkins.getAllItems(Job.class);
        if (jobs.isEmpty()) {
            LOG(Level.INFO, "Jenkins doesn't have any jobs?");
            return true;
        }

        // runUrl will be something like "job/<name>/<runId>/" or
        // "job/<folder>/job/<name>/<runId>/" if nested
        // this is the trick how we get our job name and run id
        String runUrl = pod.getMetadata().getAnnotations().get("runUrl");
        if (runUrl == null) {
            LOG(Level.INFO, "The pod has no required 'runUrl' annotation");
            return true;
        }

        // everything is in place, get the result
        Result result = getResultForJob(runUrl, jobs);
        if (result == null) {
            // we couldn't get the result for some reason
            LOG(Level.INFO, "Couldn't find the result for runUrl: " + runUrl);
            return true;
        }

        // finally, delete only if successful
        boolean delete = result.equals(Result.SUCCESS);
        LOG(Level.FINE, "delete = " + delete);
        return delete;
    }

    /**
     * Split up the runUrl string and return the run id
     *
     * @param runUrl the "runUrl" annotation of the kubernetes pod
     * @return the run id as a string
     */
    public String getRunId(String runUrl) {
        // extract the relevant parts
        String[] parts = runUrl.split("/");

        if (parts.length < 3) {
            LOG(Level.INFO, "runUrl has unknown format: " + runUrl);
            return null;
        }

        return parts[parts.length - 1].trim();
    }

    /**
     * Filter the entire job list down to the one job that we're looking for
     *
     * @param runUrl the "runUrl" annotation of the kubernetes pod
     * @param jobs   the list of all Jenkins jobs
     * @return the matching job, if successful, or null on error
     */
    public Job getJob(String runUrl, List<Job> jobs) {
        // strip the runId to enable matching by jobUrl
        Pattern pattern = Pattern.compile("(^job.+/)[0-9]+/?$");
        Matcher matcher = pattern.matcher(runUrl);
        String jobUrl = matcher.group(1);

        // find the jobs that match the shortened runUrl annotation
        // it should be only one
        List<Job> matchingJobs = jobs.stream().filter(t -> jobUrl.equals(t.getUrl())).collect(Collectors.toList());

        // we expect to find exactly one job
        if (matchingJobs.size() != 1) {
            LOG(Level.INFO, "For some reason we found multiple matching jobs: " + matchingJobs.size());
            return null;
        }

        return matchingJobs.get(0);
    }

    /**
     * Get the result for a particular Jenkins job
     *
     * @param runUrl the "runUrl" annotation of the kubernetes pod
     * @param jobs   the list of all Jenkins jobs
     * @return the job results, if successful, or null on error
     */
    public Result getResultForJob(String runUrl, List<Job> jobs) {
        // get the id of this particular run
        String runId = getRunId(runUrl);
        if (runId == null) {
            LOG(Level.INFO, "Couldn't get the runId");
            return null;
        }

        // get a reference to the job that started the pod
        Job job = getJob(runUrl, jobs);
        if (job == null) {
            LOG(Level.INFO, "Can't find the job for runUrl: " + runUrl);
            return null;
        }

        // use job and runId to find the particular run
        Run run = job.getBuild(runId);
        if (run == null) {
            LOG(Level.INFO, "Couldn't find the run for runUrl: " + runUrl);
            return null;
        }

        // get the result
        Result result = run.getResult();

        // and then this sometimes happens: the run has finished and
        // Jenkins asks if the pod should be deleted, but the result
        // is actually still null. We just repeat querying for 30
        // seconds and then abort if it's still not available
        int maxRounds = 30; // arbitrary

        while (result == null && maxRounds > 0) {
            LOG(Level.FINE, "result == null, waiting...");

            maxRounds--;

            try {
                Thread.sleep(Duration.ofSeconds(1).toMillis());
            } catch (Exception e) {
                LOG(Level.INFO, "Thread.sleep failed: " + e.getMessage());
            }

            // retry getting the result
            result = run.getResult();
        }

        // done
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof OnJobFailure) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        return Messages.on_Job_Failure();
    }

    @Extension
    @Symbol("onJobFailure")
    public static class DescriptorImpl extends PodRetentionDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.on_Job_Failure();
        }
    }
}
