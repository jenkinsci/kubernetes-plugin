package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
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

import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.CheckForNull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubernetesFactoryAdapter  {

    private final String serviceAddress;
    @CheckForNull
    private final String caCertData;
    @CheckForNull
    private final UsernamePasswordCredentials credentials;

    public KubernetesFactoryAdapter(String serviceAddress, @CheckForNull String caCertData,
            @CheckForNull String credentials) {
        this.serviceAddress = serviceAddress;
        this.caCertData = caCertData;
        this.credentials = credentials != null ? getCredentials(credentials) : null;
    }

    private UsernamePasswordCredentials getCredentials(String credentials) {
        return CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentials)
        );
    }

    public Kubernetes createKubernetes() {
        WebClient webClient = createWebClient();
        return JAXRSClientFactory.fromClient(webClient, Kubernetes.class);
    }

    /**
     * adapted from {@link KubernetesFactory#createWebClient(java.lang.String)} to offer programmatic configuration
     * @return
     */
    private WebClient createWebClient() {
        List<Object> providers = createProviders();

        AuthorizationHeaderFilter authorizationHeaderFilter = new AuthorizationHeaderFilter();
        providers.add(authorizationHeaderFilter);

        WebClient webClient = WebClient.create(serviceAddress, providers);
        if (credentials != null) {
            WebClients.configureUserAndPassword(webClient, credentials.getUsername(),
                    Secret.toString(credentials.getPassword()));
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
