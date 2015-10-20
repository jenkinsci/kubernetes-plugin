package org.csanchez.jenkins.plugins.kubernetes;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public interface TokenProducer {
    String getToken(String serviceAddress, String caCertData, boolean skipTlsVerify) throws IOException;
}
