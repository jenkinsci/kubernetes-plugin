package org.csanchez.jenkins.plugins.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.cfg.Annotations;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import io.fabric8.kubernetes.api.ExceptionResponseMapper;
import io.fabric8.kubernetes.api.Kubernetes;
import io.fabric8.kubernetes.api.KubernetesFactory;
import io.fabric8.utils.cxf.AuthorizationHeaderFilter;
import io.fabric8.utils.cxf.WebClients;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class KubernetesFactoryAdapter  {

    private final String serviceAddress;
    private final String caCertData;
    private final String username;
    private final String password;

    public KubernetesFactoryAdapter(String serviceAddress, String caCertData, String username, String password) {
        this.serviceAddress = serviceAddress;
        this.caCertData = caCertData;
        this.username = username;
        this.password = password;
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
        WebClients.configureUserAndPassword(webClient, username, password);
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
