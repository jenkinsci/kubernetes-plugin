package org.csanchez.jenkins.plugins.kubernetes;

import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsScope;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Deprecated
public class OpenShiftBearerTokenCredentialImpl
        extends org.jenkinsci.plugins.kubernetes.credentials.OpenShiftBearerTokenCredentialImpl {

    private static final long serialVersionUID = -3725963485838773012L;

    @DataBoundConstructor
    public OpenShiftBearerTokenCredentialImpl(CredentialsScope scope, String id, String description, String username,
            String password) {
        super(scope, id, description, username, password);
    }

}
