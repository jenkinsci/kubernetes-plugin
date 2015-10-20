package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.Extension;
import hudson.util.Secret;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
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

        URI uri = null;
        try {
            uri = new URI(serviceAddress);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid server URL "+serviceAddress, e);
        }

        final HttpClientBuilder builder = HttpClients.custom()
                .setRedirectStrategy(NO_REDIRECT);

        if (skipTlsVerify || caCertData != null) {
            final SSLContextBuilder sslBuilder = new SSLContextBuilder();
            HostnameVerifier hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
            try {
                if (skipTlsVerify) {
                    sslBuilder.loadTrustMaterial(null, ALWAYS);
                    hostnameVerifier = NoopHostnameVerifier.INSTANCE;
                }
                else if (caCertData != null) {
                    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                    ks.load(null);
                    CertificateFactory f = CertificateFactory.getInstance("X509");
                    X509Certificate cert = (X509Certificate) f.generateCertificate(new Base64InputStream(new ByteArrayInputStream(caCertData.getBytes())));
                    ks.setCertificateEntry(uri.getHost(), cert);
                    sslBuilder.loadTrustMaterial(ks, null);
                }

                builder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslBuilder.build(), hostnameVerifier));
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        HttpGet authorize = new HttpGet(serviceAddress + "/oauth/authorize?client_id=openshift-challenging-client&response_type=token");
        authorize.setHeader("Authorization", "Basic "+ Base64.getEncoder().encodeToString((getUsername()+':'+Secret.toString(getPassword())).getBytes()));
        final CloseableHttpResponse response = builder.build().execute(authorize);

        if (response.getStatusLine().getStatusCode() != 302) {
            throw new IOException("Failed to get an OAuth access token " + response.getStatusLine().getStatusCode());
        }

        String location = response.getFirstHeader("Location").getValue();
        String parameters = location.substring(location.indexOf('#')+1);
        List<NameValuePair> pairs = URLEncodedUtils.parse(parameters, Charset.forName("UTF-8"));
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

    private static TrustStrategy ALWAYS = new TrustStrategy() {

        @Override
        public boolean isTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
            return true;
        }
    };

    private static RedirectStrategy NO_REDIRECT = new RedirectStrategy() {
        @Override
        public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
            return false;
        }

        @Override
        public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
            return null;
        }
    };
}
