package org.csanchez.jenkins.plugins.kubernetes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Deprecated
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_INTERFACE")
public interface TokenProducer extends org.jenkinsci.plugins.kubernetes.credentials.TokenProducer {
}
