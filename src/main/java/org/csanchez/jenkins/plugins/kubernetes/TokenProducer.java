package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;

import com.cloudbees.plugins.credentials.common.StandardCredentials;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface TokenProducer extends StandardCredentials {
    String getToken(String serviceAddress, String caCertData, boolean skipTlsVerify) throws IOException;
}
