package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import io.jenkins.cli.shaded.org.apache.commons.io.FileUtils;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import jenkins.security.FIPS140;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class KubernetesCloudFIPSTest {

    @ClassRule
    public static FlagRule<String> fipsFlag = FlagRule.systemProperty(FIPS140.class.getName() + ".COMPLIANCE", "true");

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    @Issue("BEE-73460")
    public void onlyFipsCompliantValuesAreAcceptedTest() throws IOException {
        KubernetesCloud cloud = new KubernetesCloud("test-cloud");
        assertThrows(IllegalArgumentException.class, () -> cloud.setSkipTlsVerify(true));
        cloud.setSkipTlsVerify(false);
        assertThrows(IllegalArgumentException.class, () -> cloud.setServerUrl("http://example.org"));
        cloud.setServerUrl("https://example.org");
        assertThrows(IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("not-a-cert")));
        assertThrows(IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("rsa1024")));
        cloud.setServerCertificate(getCert("rsa2048"));
        assertThrows(IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("dsa1024")));
        cloud.setServerCertificate(getCert("dsa2048"));
        assertThrows(IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("ecdsa192")));
        cloud.setServerCertificate(getCert("ecdsa224"));
    }

    @Test
    @Issue("BEE-73460")
    @LocalData
    public void nonCompliantCloudsAreCleanedTest() {
        assertThat("compliant-cloud is loaded", r.jenkins.getCloud("compliant-cloud"), notNullValue());
        assertThat("with-skip-tls is not loaded", r.jenkins.getCloud("with-skip-tls"), nullValue());
        assertThat("with-http-endpoint is not loaded", r.jenkins.getCloud("with-http-endpoint"), nullValue());
        assertThat("with-invalid-cert is not loaded", r.jenkins.getCloud("with-invalid-cert"), nullValue());
    }

    private String getCert(String alg) throws IOException {
        return FileUtils.readFileToString(
                Paths.get("src/test/resources/org/csanchez/jenkins/plugins/kubernetes/KubernetesCloudFIPSTest/certs")
                        .resolve(alg)
                        .toFile(),
                Charset.defaultCharset());
    }
}
