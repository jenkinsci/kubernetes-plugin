package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.*;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class BearerTokenCredential extends BaseStandardCredentials {

    private final Secret token;

    @DataBoundConstructor
    public BearerTokenCredential(CredentialsScope scope, String id, String description, String token) {
        super(scope, id, description);
        this.token = Secret.fromString(token);
    }

    public String getToken() {
        return Secret.toString(token);
    }

    @Extension
    public static class DescriptorImpl extends BaseStandardCredentialsDescriptor {

        @Override
        public String getDisplayName() {
            return "OAuth Bearer token";
        }
    }
}
