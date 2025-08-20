package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.ExtensionList;
import io.jenkins.cli.shaded.org.apache.commons.io.FileUtils;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import jenkins.security.FIPS140;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class KubernetesCloudFIPSTest {

    private static String fipsFlag;

    private JenkinsRule r;

    @BeforeAll
    static void beforeAll() {
        fipsFlag = System.setProperty(FIPS140.class.getName() + ".COMPLIANCE", "true");
    }

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @AfterAll
    static void afterAll() {
        if (fipsFlag != null) {
            System.setProperty(FIPS140.class.getName() + ".COMPLIANCE", fipsFlag);
        } else {
            System.clearProperty(FIPS140.class.getName() + ".COMPLIANCE");
        }
    }

    @Test
    @Issue("JENKINS-73460")
    void onlyFipsCompliantValuesAreAcceptedTest() throws Exception {
        KubernetesCloud cloud = new KubernetesCloud("test-cloud");
        assertThrows(IllegalArgumentException.class, () -> cloud.setSkipTlsVerify(true));
        cloud.setSkipTlsVerify(false);
        assertThrows(IllegalArgumentException.class, () -> cloud.setServerUrl("http://example.org"));
        cloud.setServerUrl("https://example.org");
        assertThrows(
                IllegalArgumentException.class,
                () -> cloud.setServerCertificate(getCert("not-a-cert")),
                "Invalid certificates throw exception");
        Throwable exception = assertThrows(
                IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("rsa1024")), "Invalid length");
        assertThat(exception.getLocalizedMessage(), containsString("2048"));
        cloud.setServerCertificate(getCert("rsa2048"));
        exception = assertThrows(
                IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("dsa1024")), "invalid length");
        assertThat(exception.getLocalizedMessage(), containsString("2048"));
        cloud.setServerCertificate(getCert("dsa2048"));
        exception = assertThrows(
                IllegalArgumentException.class,
                () -> cloud.setServerCertificate(getCert("ecdsa192")),
                "Invalid field size");
        assertThat(exception.getLocalizedMessage(), containsString("224"));
        cloud.setServerCertificate(getCert("ecdsa224"));
    }

    @Test
    @Issue("JENKINS-73460")
    @LocalData
    void nonCompliantCloudsAreCleanedTest() {
        assertThat("compliant-cloud is loaded", r.jenkins.getCloud("compliant-cloud"), notNullValue());
        assertThat(
                "no certificate is a valid cloud",
                r.jenkins.getCloud("no-certificate-compliant-cloud"),
                notNullValue());
        assertThat("with-skip-tls is not loaded", r.jenkins.getCloud("with-skip-tls"), nullValue());
        assertThat("with-http-endpoint is not loaded", r.jenkins.getCloud("with-http-endpoint"), nullValue());
        assertThat("with-invalid-cert is not loaded", r.jenkins.getCloud("with-invalid-cert"), nullValue());
    }

    @Test
    @Issue("JENKINS-73460")
    void formValidationTest() throws Exception {
        ExtensionList<KubernetesCloud.DescriptorImpl> descriptors =
                ExtensionList.lookup(KubernetesCloud.DescriptorImpl.class);
        KubernetesCloud.DescriptorImpl descriptor = descriptors.stream()
                .filter(d -> d.getClass().isAssignableFrom(KubernetesCloud.DescriptorImpl.class))
                .findFirst()
                .orElseGet(KubernetesCloud.DescriptorImpl::new);
        assertThat(
                "Valid url doesn't raise error",
                descriptor.doCheckServerUrl(r.jenkins, "https://eample.org").getMessage(),
                nullValue());
        assertThat(
                "Invalid url raises error",
                descriptor.doCheckServerUrl(r.jenkins, "http://eample.org").getMessage(),
                notNullValue());
        assertThat(
                "Valid cert doesn't raise error",
                descriptor
                        .doCheckServerCertificate(r.jenkins, getCert("rsa2048"))
                        .getMessage(),
                nullValue());
        assertThat(
                "Invalid cert raises error",
                descriptor
                        .doCheckServerCertificate(r.jenkins, getCert("rsa1024"))
                        .getMessage(),
                notNullValue());
        assertThat(
                "No TLS skip doesn't raise error",
                descriptor.doCheckSkipTlsVerify(r.jenkins, false).getMessage(),
                nullValue());
        assertThat(
                "TLS skip raises error",
                descriptor.doCheckSkipTlsVerify(r.jenkins, true).getMessage(),
                notNullValue());
    }

    private String getCert(String alg) throws Exception {
        return FileUtils.readFileToString(
                Paths.get("src/test/resources/org/csanchez/jenkins/plugins/kubernetes/KubernetesCloudFIPSTest/certs")
                        .resolve(alg)
                        .toFile(),
                Charset.defaultCharset());
    }
}
