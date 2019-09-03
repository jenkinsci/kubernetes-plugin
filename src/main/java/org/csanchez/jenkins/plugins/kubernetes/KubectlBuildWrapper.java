package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthKubeconfig;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuthException;
import org.jenkinsci.plugins.kubernetes.auth.KubernetesAuth;
import org.jenkinsci.plugins.kubernetes.credentials.Utils;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubectlBuildWrapper extends SimpleBuildWrapper {


    private final String serverUrl;
    private final String credentialsId;
    private final String caCertificate;

    @DataBoundConstructor
    public KubectlBuildWrapper(@Nonnull String serverUrl, @Nonnull String credentialsId,
            @Nonnull String caCertificate) {
        this.serverUrl = serverUrl;
        this.credentialsId = credentialsId;
        this.caCertificate = caCertificate;
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

    private String buildKubeConfig(KubernetesAuth auth) throws JsonProcessingException {
        ConfigBuilder b = new ConfigBuilder();
        // setup cluster
        Cluster c = new Cluster();
        c.setServer(getServerUrl());
        if (caCertificate != null && !caCertificate.isEmpty()) {
            c.setCertificateAuthorityData(Utils.wrapCertificate(caCertificate));
        } else {
            c.setInsecureSkipTlsVerify(true);
        }
        b.addNewCluster().withName("k8s").withCluster(c).endCluster();
        // setup user
        AuthInfoBuilder authInfoBuilder = new AuthInfoBuilder();
        auth.decorate(authInfoBuilder);
        b.addNewUser().withName("cluster-admin").withUser(authInfoBuilder.build()).endUser();
        // setup context
        b.addNewContext().withName("k8s").withNewContext().withCluster("k8s").withUser("cluster-admin").endContext().endContext();
        return SerializationUtils.getMapper().writeValueAsString(b.build());
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {

        FilePath configFile = workspace.createTempFile(".kube", "config");
        Set<String> tempFiles = newHashSet(configFile.getRemote());

        context.env("KUBECONFIG", configFile.getRemote());
        context.setDisposer(new CleanupDisposer(tempFiles));

        final KubernetesAuth auth;
        try {
            auth = KubernetesAuthFactory.fromCredentialsId(credentialsId, serverUrl, null, true);
        } catch (KubernetesAuthException e) {
            throw new AbortException(e.getMessage());
        }

        if (auth == null) {
            throw new AbortException("No credentials defined to setup Kubernetes CLI");
        }

        // create Kubeconfig
        try (Writer w = new OutputStreamWriter(new FileOutputStream(configFile.getRemote()), "UTF-8")) {
            if (auth instanceof KubernetesAuthKubeconfig) {
                w.write(((KubernetesAuthKubeconfig) auth).getKubeconfig());
            } else {
                w.write(buildKubeConfig(auth));
            }
        }

        int status = launcher.launch().cmdAsSingleString("kubectl version").join();
        if (status != 0) throw new IOException("Failed to run kubectl version " + status);
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
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                                    CredentialsMatchers.instanceOf(TokenProducer.class),
                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
                                    CredentialsMatchers.instanceOf(FileCredentials.class),
                                    AuthenticationTokens.matcher(KubernetesAuth.class)
                            ),
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
