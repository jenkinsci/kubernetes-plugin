package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.security.ACL;
import jenkins.authentication.tokens.api.AuthenticationTokens;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;

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
        if (c instanceof TokenProducer) {
            return new KubernetesAuthToken(((TokenProducer) c).getToken(serviceAddress, caCertData, skipTlsVerify));
        } else {
            throw new Exception("Unable to use " + credentialsId + " for authentication");
        }
    }

}
