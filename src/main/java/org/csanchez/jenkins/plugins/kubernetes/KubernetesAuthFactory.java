package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.security.ACL;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;

import org.jenkinsci.plugins.kubernetes.auth.*;

public abstract class KubernetesAuthFactory {

    private static StandardCredentials getCredentials(String credentialsId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()
                ),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    private static StandardCredentials getAuthenticationTokenCredentials(String credentialsId) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        Collections.emptyList()
                ),
                CredentialsMatchers.allOf(
                        AuthenticationTokens.matcher(KubernetesAuth.class),
                        CredentialsMatchers.withId(credentialsId)
                )
        );
    }

    public static KubernetesAuth fromCredentialsId(String credentialsId, String serviceAddress, String caCertData, Boolean skipTlsVerify) throws Exception {
        StandardCredentials c = getAuthenticationTokenCredentials(credentialsId);
        if (c != null) {
            return AuthenticationTokens.convert(KubernetesAuth.class, c);
        }
        c = getCredentials(credentialsId);
        if (c instanceof StringCredentials) {
            return new KubernetesAuthToken(((StringCredentials) c).getSecret().getPlainText());
        } else if (c instanceof TokenProducer) {
            return new KubernetesAuthToken(((TokenProducer) c).getToken(serviceAddress, caCertData, skipTlsVerify));
        } else if (c instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials usernamePassword = (UsernamePasswordCredentials) c;
            return new KubernetesAuthUsernamePassword(
                    usernamePassword.getUsername(),
                    usernamePassword.getPassword().getPlainText()
            );
        } else if (c instanceof StandardCertificateCredentials) {
            StandardCertificateCredentials certificateCredentials = (StandardCertificateCredentials) c;
            KeyStore keyStore = certificateCredentials.getKeyStore();
            String password = certificateCredentials.getPassword().getPlainText();
            String alias = keyStore.aliases().nextElement();
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            return new KubernetesAuthCertificate(
                    Utils.wrapWithMarker(
                        Utils.BEGIN_CERTIFICATE,
                        Utils.END_CERTIFICATE,
                        Base64.encodeBase64String(
                            certificate.getEncoded()
                        )
                    ),
                    Utils.wrapWithMarker(
                        Utils.BEGIN_PRIVATE_KEY,
                        Utils.END_PRIVATE_KEY,
                        Base64.encodeBase64String(
                            keyStore.getKey(alias, password.toCharArray()).getEncoded()
                        )
                    ),
                    password
            );
        } else if (c instanceof DockerServerCredentials) {
            DockerServerCredentials certificateCredentials = (DockerServerCredentials) c;
            return new KubernetesAuthCertificate(certificateCredentials.getClientCertificate(), certificateCredentials.getClientKey());
        } else if (c instanceof FileCredentials) {
            try (InputStream is = ((FileCredentials) c).getContent()) {
                return new KubernetesAuthKubeconfig(IOUtils.toString(is, StandardCharsets.UTF_8));
            }
        } else {
            throw new Exception("Unable to use " + credentialsId + " for authentication");
        }
    }

}
