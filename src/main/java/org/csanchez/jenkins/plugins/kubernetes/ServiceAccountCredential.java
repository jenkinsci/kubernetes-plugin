package org.csanchez.jenkins.plugins.kubernetes;

import org.jenkinsci.plugins.kubernetes.credentials.FileSystemServiceAccountCredential;
import org.jenkinsci.plugins.kubernetes.credentials.TokenProducer;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsScope;

/**
 * Read the OAuth bearer token from service account file provisioned by kubernetes
 * <a href="http://kubernetes.io/v1.0/docs/admin/service-accounts-admin.html">Service Account Admission Controller</a>
 * when Jenkins itself is deployed inside a Pod.
 *
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Deprecated
public class ServiceAccountCredential extends FileSystemServiceAccountCredential implements TokenProducer {

    private static final long serialVersionUID = 2739355565227800401L;

    @DataBoundConstructor
    public ServiceAccountCredential(CredentialsScope scope, String id, String description) {
        super(scope, id, description);
    }

}
