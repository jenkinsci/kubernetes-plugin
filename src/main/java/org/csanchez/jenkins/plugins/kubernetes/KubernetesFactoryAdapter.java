package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.CertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.cfg.Annotations;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import hudson.security.ACL;
import hudson.util.Secret;
import io.fabric8.kubernetes.api.ExceptionResponseMapper;
import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesFactory;
import io.fabric8.utils.cxf.AuthorizationHeaderFilter;
import io.fabric8.utils.cxf.WebClients;
import jenkins.model.Jenkins;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.auth.HttpAuthSupplier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubernetesFactoryAdapter  {

    private final String serviceAddress;
    @CheckForNull
    private final String caCertData;
    @CheckForNull
    private final StandardCredentials credentials;
    
    private static final Logger LOGGER = Logger.getLogger(KubernetesCloud.class.getName());
    
    @CheckForNull
    private static final String serviceAccountTokenPath = "/run/secrets/kubernetes.io/serviceaccount/token";


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

    public Kubernetes createKubernetes() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException {
        WebClient webClient = createWebClient();
        return JAXRSClientFactory.fromClient(webClient, Kubernetes.class);
    }

    /**
     * adapted from {@link KubernetesFactory#createWebClient(java.lang.String)} to offer programmatic configuration
     * @return
     */
    private WebClient createWebClient() throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        List<Object> providers = createProviders();

        AuthorizationHeaderFilter authorizationHeaderFilter = new AuthorizationHeaderFilter();
        providers.add(authorizationHeaderFilter);

        WebClient webClient = WebClient.create(serviceAddress, providers);
        if (credentials != null) {
            if (credentials instanceof UsernamePasswordCredentials) {
                UsernamePasswordCredentials usernamePassword = (UsernamePasswordCredentials) credentials;
                WebClients.configureUserAndPassword(webClient, usernamePassword.getUsername(),
                        Secret.toString(usernamePassword.getPassword()));
            } else if (credentials instanceof BearerTokenCredential) {

                final HTTPConduit conduit = WebClient.getConfig(webClient).getHttpConduit();
                conduit.setAuthSupplier(new HttpAuthSupplier() {
                    @Override
                    public boolean requiresRequestCaching() {
                        return false;
                    }

                    @Override
                    public String getAuthorization(AuthorizationPolicy authorizationPolicy, URI uri, Message message, String s) {
                        return "Bearer " + ((BearerTokenCredential) credentials).getToken();
                    }
                });
            }
        } else {
          final File token = new File(serviceAccountTokenPath);
          final String tokenContent;
          try {
            tokenContent = IOUtils.toString(new FileInputStream(token), "UTF-8");
            final HTTPConduit conduit = WebClient.getConfig(webClient).getHttpConduit();
            conduit.setAuthSupplier(new HttpAuthSupplier() {
              @Override
              public boolean requiresRequestCaching() {
                return false;
              }

              @Override
              public String getAuthorization(AuthorizationPolicy authorizationPolicy, URI uri, Message message,
                  String s) {
                return "Bearer " + tokenContent;
              }
            });
          } catch (FileNotFoundException e) {
            LOGGER.log(Level.WARNING, "The service account token file does not exists: #", e);
          } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to read service account token file: #", e);
          }
        }

        if (skipTlsVerify) {
            WebClients.disableSslChecks(webClient);
        }

        if (caCertData != null) {
            WebClients.configureCaCert(webClient, caCertData, null);
        }
        return webClient;
    }

    private List<Object> createProviders() {
        List<Object> providers = new ArrayList<Object>();
        Annotations[] annotationsToUse = JacksonJaxbJsonProvider.DEFAULT_ANNOTATIONS;
        ObjectMapper objectMapper = KubernetesFactory.createObjectMapper();
        providers.add(new JacksonJaxbJsonProvider(objectMapper, annotationsToUse));
        providers.add(new KubernetesFactory.PlainTextJacksonProvider(objectMapper, annotationsToUse));
        providers.add(new ExceptionResponseMapper());
        //providers.add(new JacksonIntOrStringConfig(objectMapper));
        return providers;
    }
}
