package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubectlBuildWrapper extends SimpleBuildWrapper {

    private final String serverUrl;
    private final String credentialsId;

    @DataBoundConstructor
    public KubectlBuildWrapper(@Nonnull String serverUrl, @Nonnull String credentialsId) {
        this.serverUrl = serverUrl;
        this.credentialsId = credentialsId;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {

        FilePath configFile = workspace.createTempFile(".kube", "config");

        int status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " set-cluster k8s --server=" + serverUrl + " --insecure-skip-tls-verify=true")
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);

        final UsernamePasswordCredentials c = getCredentials();

        status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " set-credentials cluster-admin --username=" + c.getUsername() + " --password=" + Secret.toString(c.getPassword()))
                .masks(false, false, false, false, false, false, true)
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);

        status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " set-context k8s --cluster=k8s --user=cluster-admin")
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);

        status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=" + configFile.getRemote() + " use-context k8s")
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);

        context.setDisposer(new CleanupDisposer(configFile.getRemote()));

        context.env("KUBECONFIG", configFile.getRemote());
    }

    private UsernamePasswordCredentials getCredentials() {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId)
        );

    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Setup Kubernetes CLI (kubectl)";
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String serverUrl) {
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                            CredentialsProvider.lookupCredentials(
                                    StandardCredentials.class,
                                    item,
                                    null,
                                    URIRequirementBuilder.fromUri(serverUrl).build()
                            )
                    );

        }

    }

    private static class CleanupDisposer extends Disposer {

        private String configFile;

        public CleanupDisposer(String configFile) {
            this.configFile = configFile;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            workspace.child(configFile).delete();
        }
    }
}
