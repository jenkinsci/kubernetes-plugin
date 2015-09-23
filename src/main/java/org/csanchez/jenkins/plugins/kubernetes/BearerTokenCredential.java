package org.csanchez.jenkins.plugins.kubernetes;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface BearerTokenCredential extends Credentials, StandardCredentials {
    String getToken();
}
