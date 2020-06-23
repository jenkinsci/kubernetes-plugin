package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthConfig;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuth;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubectlBuildWrapper extends SimpleBuildWrapper {

    private String serverUrl;
    private String credentialsId;
    private String caCertificate;

    @DataBoundConstructor
    public KubectlBuildWrapper(@Nonnull String serverUrl, @Nonnull String credentialsId,
            @Nonnull String caCertificate) {
        this.serverUrl = serverUrl;
        this.credentialsId = Util.fixEmpty(credentialsId);
        this.caCertificate = Util.fixEmptyAndTrim(caCertificate);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getCaCertificate() {
        return caCertificate;
    }

    protected Object readResolve() {
        this.credentialsId = Util.fixEmpty(credentialsId);
        this.caCertificate = Util.fixEmptyAndTrim(caCertificate);
        return this;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        if (credentialsId == null) {
            throw new AbortException("No credentials defined to setup Kubernetes CLI");
        }
        workspace.mkdirs();
        FilePath configFile = workspace.createTempFile(".kube", "config");
        Set<String> tempFiles = newHashSet(configFile.getRemote());

        context.env("KUBECONFIG", configFile.getRemote());
        context.setDisposer(new CleanupDisposer(tempFiles));

        StandardCredentials credentials = CredentialsProvider.findCredentialById(credentialsId, StandardCredentials.class, build, Collections.emptyList());
        if (credentials == null) {
            throw new AbortException("No credentials found for id \"" + credentialsId + "\"");
        }
        KubernetesAuth auth = AuthenticationTokens.convert(KubernetesAuth.class, credentials);
        if (auth == null) {
            throw new AbortException("Unsupported Credentials type " + credentials.getClass().getName());
        }
        try (Writer w = new OutputStreamWriter(configFile.write(), StandardCharsets.UTF_8)) {
            w.write(auth.buildKubeConfig(new KubernetesAuthConfig(getServerUrl(), getCaCertificate(), getCaCertificate() == null)));
        } catch (KubernetesAuthException e) {
            throw new AbortException(e.getMessage());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String cmd = "kubectl version";
        int status = launcher.launch().cmdAsSingleString(cmd).stdout(out).stderr(err).quiet(true).envs("KUBECONFIG="+configFile.getRemote()).join();
        if (status != 0) {
            StringBuilder msgBuilder = new StringBuilder("Failed to run \"").append(cmd).append("\". Returned status code ").append(status).append(".\n");
            msgBuilder.append("stdout:\n").append(out).append("\n");
            msgBuilder.append("stderr:\n").append(err);
            throw new AbortException(msgBuilder.toString());
        }
    }

    @Extension
    @Symbol("kubeconfig")
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
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            result.includeMatchingAs(
                    ACL.SYSTEM,
                    item,
                    StandardCredentials.class,
                    URIRequirementBuilder.fromUri(serverUrl).build(),
                    CredentialsMatchers.anyOf(
                            CredentialsMatchers.instanceOf(org.jenkinsci.plugins.kubernetes.credentials.TokenProducer.class),
                            AuthenticationTokens.matcher(KubernetesAuth.class)
                    )
            );
            return result;
        }
    }

    private static class CleanupDisposer extends Disposer {

        private static final long serialVersionUID = 3006113419319201358L;
        private Set<String> configFiles;

        public CleanupDisposer(Set<String> tempFiles) {
            this.configFiles = tempFiles;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            for (String configFile : configFiles) {
                workspace.child(configFile).delete();
            }
        }
    }
}
