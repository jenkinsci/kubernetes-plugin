package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.AbortException;
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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubectlBuildWrapper extends SimpleBuildWrapper {

    private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

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

        String tlsConfig;
        if (caCertificate != null && !caCertificate.isEmpty()) {
            FilePath caCrtFile = workspace.createTempFile("cert-auth", "crt");
            String ca = caCertificate;
            if (!ca.startsWith(BEGIN_CERTIFICATE)) {
                ca = wrapWithMarker(BEGIN_CERTIFICATE, END_CERTIFICATE, ca);
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

        final StandardCredentials c = getCredentials();

        String login;
        if (c == null) {
            throw new AbortException("No credentials defined to setup Kubernetes CLI");
        } else if (c instanceof TokenProducer) {
            login = "--token=" + ((TokenProducer) c).getToken(serverUrl, null, true);
        } else if (c instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) c;
            login = "--username=" + upc.getUsername() + " --password=" + Secret.toString(upc.getPassword());
        } else if (c instanceof StandardCertificateCredentials) {
            StandardCertificateCredentials scc = (StandardCertificateCredentials) c;
            KeyStore keyStore = scc.getKeyStore();
            String alias;
            try {
                alias = keyStore.aliases().nextElement();
                X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
                Key key = keyStore.getKey(alias, Secret.toString(scc.getPassword()).toCharArray());
                FilePath clientCrtFile = workspace.createTempFile("client", "crt");
                FilePath clientKeyFile = workspace.createTempFile("client", "key");
                String encodedClientCrt = wrapWithMarker(BEGIN_CERTIFICATE, END_CERTIFICATE,
                        Base64.encodeBase64String(certificate.getEncoded()));
                String encodedClientKey = wrapWithMarker(BEGIN_PRIVATE_KEY, END_PRIVATE_KEY,
                        Base64.encodeBase64String(key.getEncoded()));
                clientCrtFile.write(encodedClientCrt, null);
                clientKeyFile.write(encodedClientKey, null);
                tempFiles.add(clientCrtFile.getRemote());
                tempFiles.add(clientKeyFile.getRemote());
                login = "--client-certificate=" + clientCrtFile.getRemote() + " --client-key="
                        + clientKeyFile.getRemote();
            } catch (KeyStoreException e) {
                throw new AbortException(e.getMessage());
            } catch (UnrecoverableKeyException e) {
                throw new AbortException(e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                throw new AbortException(e.getMessage());
            } catch (CertificateEncodingException e) {
                throw new AbortException(e.getMessage());
            }
        } else {
            throw new AbortException("Unsupported Credentials type " + c.getClass().getName());
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

        context.setDisposer(new CleanupDisposer(tempFiles));

        context.env("KUBECONFIG", configFile.getRemote());
    }

    /**
     * Get the {@link StandardCredentials}.
     *
     * @return the credentials matching the {@link #credentialsId} or {@code null} is {@code #credentialsId} is blank
     * @throws AbortException if no {@link StandardCredentials} matching {@link #credentialsId} is found
     */
    @CheckForNull
    private StandardCredentials getCredentials() throws AbortException {
        if (StringUtils.isBlank(credentialsId)) {
            return null;
        }
        StandardCredentials result = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId)
        );
        if (result == null) {
            throw new AbortException("No credentials found for id \"" + credentialsId + "\"");
        }
        return result;
    }

    private static String wrapWithMarker(String begin, String end, String encodedBody) {
        return new StringBuilder(begin).append("\n")
                .append(encodedBody).append("\n")
                .append(end)
                .toString();
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
                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class)
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
