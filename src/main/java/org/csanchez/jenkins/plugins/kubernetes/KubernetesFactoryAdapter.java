package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import hudson.util.Secret;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubernetesFactoryAdapter {

    private final String serviceAddress;
    @CheckForNull
    private final String caCertData;
    @CheckForNull
    private final StandardCredentials credentials;

    private final boolean skipTlsVerify;

    public KubernetesFactoryAdapter(String serviceAddress, @CheckForNull String caCertData,
                                    @CheckForNull String credentials, boolean skipTlsVerify) {
        this.serviceAddress = serviceAddress;
        this.caCertData = caCertData;
        this.credentials = credentials != null ? getCredentials(credentials) : null;
        this.skipTlsVerify = skipTlsVerify;
    }

    private StandardCredentials getCredentials(String credentials) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentials)
        );
    }

    public KubernetesClient createClient()  {
        ConfigBuilder builder = new ConfigBuilder().withMasterUrl(serviceAddress);
        if (credentials != null) {
            if (credentials instanceof UsernamePasswordCredentials) {
                UsernamePasswordCredentials usernamePassword = (UsernamePasswordCredentials) credentials;
                builder.withUsername(usernamePassword.getUsername()).withPassword(Secret.toString(usernamePassword.getPassword()));
            } else if (credentials instanceof BearerTokenCredential) {
                builder.withOauthToken(((BearerTokenCredential) credentials).getToken());
            }
        }

        if (skipTlsVerify) {
            builder.withTrustCerts(true);
        }

        if (caCertData != null) {
            builder.withCaCertData(caCertData);
        }
        return new DefaultKubernetesClient(builder.build());
    }
}
