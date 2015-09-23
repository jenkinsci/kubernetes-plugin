package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.Extension;
import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;

/**
 * Read the OAuth bearer token from service account file provisionned by kubernetes
 * <a href="http://kubernetes.io/v1.0/docs/admin/service-accounts-admin.html">Service Account Admission Controller</a>
 * when Jenkins itself is deployed inside a Pod.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class ServiceAccountCredential extends BearerTokenCredential {

    @DataBoundConstructor
    public ServiceAccountCredential(CredentialsScope scope, String id, String description) {
        super(scope, id, description, null);
    }

    @Override
    public String getToken() {
        try {
            return FileUtils.readFileToString(new File("/run/secrets/kubernetes.io/serviceaccount/token"));
        } catch (IOException e) {
            return null;
        }
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        public DescriptorImpl() {
            if (!new File("/run/secrets/kubernetes.io/serviceaccount/token").exists()) {
                throw new RuntimeException("Jenkins isn't running inside Kubernetes with Admission Controller.");
            }
        }

        @Override
        public String getDisplayName() {
            return "Kubernetes Service Account";
        }
    }
}
