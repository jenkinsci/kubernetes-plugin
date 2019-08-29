package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
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
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.tasks.SimpleBuildWrapper;
import org.jenkinsci.plugins.kubernetes.auth.*;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.*;
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

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {

        FilePath configFile = workspace.createTempFile(".kube", "config");
        Set<String> tempFiles = newHashSet(configFile.getRemote());

        context.env("KUBECONFIG", configFile.getRemote());
        context.setDisposer(new CleanupDisposer(tempFiles));

        String tlsConfig;
        if (caCertificate != null && !caCertificate.isEmpty()) {
            FilePath caCrtFile = workspace.createTempFile("cert-auth", "crt");
            String ca = caCertificate;
            if (!ca.startsWith(Utils.BEGIN_CERTIFICATE)) {
                ca = Utils.wrapWithMarker(Utils.BEGIN_CERTIFICATE, Utils.END_CERTIFICATE, ca);
            }
            caCrtFile.write(ca, null);
            tempFiles.add(caCrtFile.getRemote());

            tlsConfig = " --certificate-authority=" + caCrtFile.getRemote();
        } else {
            tlsConfig = " --insecure-skip-tls-verify=true";
        }

        int status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=\"" + configFile.getRemote()
                        + "\" set-cluster k8s --server=" + serverUrl + tlsConfig)
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);

        final KubernetesAuth auth;
        try {
            auth = KubernetesAuthFactory.fromCredentialsId(credentialsId, serverUrl, null, true);
        } catch (Exception e) {
            throw new AbortException(e.getMessage());
        }

        if (auth == null) {
            throw new AbortException("No credentials defined to setup Kubernetes CLI");
        }

        // create Kubeconfig
        if (auth instanceof KubernetesAuthKubeconfig) {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(configFile.getRemote()), "UTF-8")) {
                w.write(((KubernetesAuthKubeconfig) auth).getKubeconfig());
            }
            return;
        }

        String login;
        if (auth instanceof KubernetesAuthToken) {
            login = "--token=" + ((KubernetesAuthToken) auth).getToken();
        } else if (auth instanceof KubernetesAuthUsernamePassword) {
            KubernetesAuthUsernamePassword upc = (KubernetesAuthUsernamePassword) auth;
            login = "--username=" + upc.getUsername() + " --password=" + upc.getPassword();
        } else if (auth instanceof KubernetesAuthCertificate) {
            KubernetesAuthCertificate certData = (KubernetesAuthCertificate) auth;
            FilePath clientCrtFile = workspace.createTempFile("client", "crt");
            FilePath clientKeyFile = workspace.createTempFile("client", "key");
            clientCrtFile.write(certData.getCertificate(), null);
            clientKeyFile.write(certData.getKey(), null);
            tempFiles.add(clientCrtFile.getRemote());
            tempFiles.add(clientKeyFile.getRemote());
            login = "--client-certificate=" + clientCrtFile.getRemote() + " --client-key="
                    + clientKeyFile.getRemote();
        } else {
            throw new AbortException("Unable to detect login method for class " + auth.getClass());
        }

        status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=\"" + configFile.getRemote() + "\" set-credentials cluster-admin " + login)
                .masks(false, false, false, false, false, false, true)
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);

        status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=\"" + configFile.getRemote() + "\" set-context k8s --cluster=k8s --user=cluster-admin")
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);

        status = launcher.launch()
                .cmdAsSingleString("kubectl config --kubeconfig=\"" + configFile.getRemote() + "\" use-context k8s")
                .join();
        if (status != 0) throw new IOException("Failed to run kubectl config "+status);
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
