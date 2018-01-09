package org.csanchez.jenkins.plugins.kubernetes;

import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsScope;

import hudson.util.Secret;

/**
 * @author <a href="mailto:andy.block@gmail.com">Andrew Block</a>
 */
@Deprecated
public class OpenShiftTokenCredentialImpl
        extends org.jenkinsci.plugins.kubernetes.credentials.OpenShiftTokenCredentialImpl {

    @DataBoundConstructor
    public OpenShiftTokenCredentialImpl(CredentialsScope scope, String id, String description, Secret secret) {
        super(scope, id, description, secret);
    }
}
