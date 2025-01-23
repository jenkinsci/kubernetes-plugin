package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsScope;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Deprecated
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public class OpenShiftBearerTokenCredentialImpl
        extends org.jenkinsci.plugins.kubernetes.credentials.OpenShiftBearerTokenCredentialImpl {

    private static final long serialVersionUID = -3725963485838773012L;

    @DataBoundConstructor
    public OpenShiftBearerTokenCredentialImpl(
            CredentialsScope scope, String id, String description, String username, String password)
            throws Descriptor.FormException {
        super(scope, id, description, username, password);
    }
}
