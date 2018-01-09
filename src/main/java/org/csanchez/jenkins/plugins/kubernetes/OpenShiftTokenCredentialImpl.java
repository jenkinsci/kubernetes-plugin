package org.csanchez.jenkins.plugins.kubernetes;

import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsScope;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.util.Secret;

/**
 * @author <a href="mailto:andy.block@gmail.com">Andrew Block</a>
 */
@Deprecated
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_SUPERCLASS")
public class OpenShiftTokenCredentialImpl
        extends org.jenkinsci.plugins.kubernetes.credentials.OpenShiftTokenCredentialImpl {

    @DataBoundConstructor
    public OpenShiftTokenCredentialImpl(CredentialsScope scope, String id, String description, Secret secret) {
        super(scope, id, description, secret);
    }
}
