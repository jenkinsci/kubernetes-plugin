package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Extension;
import hudson.util.Secret;
import io.fabric8.utils.cxf.WebClients;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class OpenShiftBearerTokenCredentialImpl extends UsernamePasswordCredentialsImpl implements TokenProducer {

    private transient AtomicReference<Token> token = new AtomicReference<Token>();

    @DataBoundConstructor
    public OpenShiftBearerTokenCredentialImpl(CredentialsScope scope, String id, String description, String username, String password) {
        super(scope, id, description, username, password);
    }

    private Object readResolve() {
        token = new AtomicReference<Token>();
        return this;
    }


    @Override
    public String getToken(String serviceAddress, String caCertData, boolean skipTlsVerify) throws IOException {
        Token t = this.token.get();
        if (t == null || System.currentTimeMillis() > t.expire) {
            t = refreshToken(serviceAddress, caCertData, skipTlsVerify);
        }

        return t.value;
    }

    private synchronized Token refreshToken(String serviceAddress, String caCertData, boolean skipTlsVerify) throws IOException {

        final WebClient webClient = WebClient.create(serviceAddress + "/oauth/authorize?client_id=openshift-challenging-client&response_type=token");
        if (skipTlsVerify) {
            WebClients.disableSslChecks(webClient);
        }

        if (caCertData != null) {
            WebClients.configureCaCert(webClient, caCertData, null);
        }

        WebClients.configureUserAndPassword(webClient, getUsername(), Secret.toString(getPassword()));

        final Response response = webClient.get();
        if (response.getStatus() != 302) {
            throw new IOException("Failed to get an OAuth access token " + response.getStatus());
        }

        String uri = response.getLocation().toString();
        String parameters = uri.substring(uri.indexOf('#')+1);
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        URLEncodedUtils.parse(pairs, new Scanner(parameters), "UTF-8");
        Token t = new Token();
        for (NameValuePair pair : pairs) {
            if (pair.getName().equals("access_token")) {
                t.value = pair.getValue();
            }
            else if (pair.getName().equals("expires_in")) {
                t.expire = System.currentTimeMillis() + Long.parseLong(pair.getValue())*1000 - 100;
            }
        }
        return t;
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "OpenShift OAuth Access token";
        }
    }

    private static class Token {
        String value;
        long expire;
    }
}
