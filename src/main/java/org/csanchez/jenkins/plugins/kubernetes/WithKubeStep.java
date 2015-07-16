package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.LauncherDecorator;
import hudson.Proc;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class WithKubeStep extends AbstractStepImpl {


    private final String serverUrl;
    private final String credentialsId;

    @DataBoundConstructor
    public WithKubeStep(@Nonnull String serverUrl, String credentialsId) {
        this.serverUrl = serverUrl;
        this.credentialsId = credentialsId;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public static class Execution extends AbstractStepExecutionImpl {

        private static final long serialVersionUID = 1;

        @Inject(optional=true) private transient WithKubeStep step;
        @StepContextParameter private transient Launcher launcher;
        @StepContextParameter private transient FilePath workspace;
        private FilePath configFile;

        @Override
        public boolean start() throws Exception {

            configFile = workspace.createTempFile(".kube", "config");

            launcher.launch()
                    .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " set-cluster k8s --server=" + step.serverUrl + " --insecure-skip-tls-verify=true")
                    .join();

            final UsernamePasswordCredentials c = getCredentials(step.credentialsId);

            launcher.launch()
                    .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " set-credentials cluster-admin --username=" + c.getUsername() + " --password=" + Secret.toString(c.getPassword()))
                    .masks(false, false, false, false, false, false, true)
                    .join();

            launcher.launch()
                    .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " set-context k8s --cluster=k8s --user=cluster-admin")
                    .join();

            launcher.launch()
                    .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " use-context k8s")
                    .join();

            getContext().newBodyInvoker()
                    .withContext(BodyInvoker.mergeLauncherDecorators(getContext().get(LauncherDecorator.class), new Decorator(configFile.getRemote())))
                    .withCallback(new Callback(configFile))
                    .start();
            return false;
        }

        private UsernamePasswordCredentials getCredentials(String credentials) {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                            Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                    CredentialsMatchers.withId(credentials)
            );
        }


        @Override
        public void stop(@Nonnull Throwable throwable) throws Exception {
            configFile.deleteRecursive();
        }
    }


    private static class Callback extends BodyExecutionCallback {

        private static final long serialVersionUID = 1;

        private FilePath configFile;

        public Callback(FilePath configFile) {
            this.configFile = configFile;
        }

        @Override
        public void onSuccess(StepContext context, Object result) {
            try {
                configFile.deleteRecursive();
            } catch (Exception e) {
            }
            context.onSuccess(result);
        }

        @Override
        public void onFailure(StepContext context, Throwable t) {
            try {
                configFile.deleteRecursive();
            } catch (Exception e) {
            }
            context.onFailure(t);
        }
    }

    private static class Decorator extends LauncherDecorator implements Serializable {

        private String configFile;

        public Decorator(String configFile) {
            this.configFile = configFile;
        }

        @Override
        public Launcher decorate(Launcher launcher, Node node) {
            return new Launcher.DecoratedLauncher(launcher) {
                @Override
                public Proc launch(ProcStarter starter) throws IOException {
                    starter.envs("KUBECONFIG="+ configFile);
                    return super.launch(starter);
                }
            };
        }
    }



    @Extension(optional = true)
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "withKubernetes";
        }

        @Override public String getDisplayName() {
            return "Run build steps with kubernetes cluster configuration";
        }

        @Override public boolean takesImplicitBlockArgument() {
            return true;
        }

    }
}
