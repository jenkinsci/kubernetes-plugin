package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;

/**
 * Read the OAuth bearer token from service account file provisioned by kubernetes
 * <a href="http://kubernetes.io/v1.0/docs/admin/service-accounts-admin.html">Service Account Admission Controller</a>
 * when Jenkins itself is deployed inside a Pod.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ServiceAccountCredential extends BaseStandardCredentials implements TokenProducer {

    private static final String SERVICEACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";

    @DataBoundConstructor
    public ServiceAccountCredential(CredentialsScope scope, String id, String description) {
        super(scope, id, description);
    }

    @Override
    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public String getToken(String serviceAddress, String caCertData, boolean skipTlsVerify) {
        try {
            return FileUtils.readFileToString(new File(SERVICEACCOUNT_TOKEN_PATH));
        } catch (IOException e) {
            return null;
        }
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
        public DescriptorImpl() {
            if (!new File(SERVICEACCOUNT_TOKEN_PATH).exists()) {
                throw new RuntimeException("Jenkins isn't running inside Kubernetes with Admission Controller.");
            }
        }

        @Override
        public String getDisplayName() {
            return "Kubernetes Service Account";
        }
    }
}
