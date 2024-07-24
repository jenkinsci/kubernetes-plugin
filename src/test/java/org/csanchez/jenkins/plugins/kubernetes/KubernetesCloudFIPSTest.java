package org.csanchez.jenkins.plugins.kubernetes;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import hudson.ExtensionList;
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
    @Issue("JENKINS-73460")
    public void onlyFipsCompliantValuesAreAcceptedTest() throws IOException {
        KubernetesCloud cloud = new KubernetesCloud("test-cloud");
        assertThrows(IllegalArgumentException.class, () -> cloud.setSkipTlsVerify(true));
        cloud.setSkipTlsVerify(false);
        assertThrows(IllegalArgumentException.class, () -> cloud.setServerUrl("http://example.org"));
        cloud.setServerUrl("https://example.org");
        assertThrows(
                "Invalid certificates throw exception",
                IllegalArgumentException.class,
                () -> cloud.setServerCertificate(getCert("not-a-cert")));
        Throwable exception = exception = assertThrows(
                "Invalid length", IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("rsa1024")));
        assertThat(exception.getLocalizedMessage(), containsString("2048"));
        cloud.setServerCertificate(getCert("rsa2048"));
        exception = assertThrows(
                "invalid length", IllegalArgumentException.class, () -> cloud.setServerCertificate(getCert("dsa1024")));
        assertThat(exception.getLocalizedMessage(), containsString("2048"));
        cloud.setServerCertificate(getCert("dsa2048"));
        exception = assertThrows(
                "Invalid field size",
                IllegalArgumentException.class,
                () -> cloud.setServerCertificate(getCert("ecdsa192")));
        assertThat(exception.getLocalizedMessage(), containsString("224"));
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

    @Test
    @Issue("BEE-73460")
    public void formValidationTest() throws IOException {
        ExtensionList<KubernetesCloud.DescriptorImpl> descriptors =
                ExtensionList.lookup(KubernetesCloud.DescriptorImpl.class);
        KubernetesCloud.DescriptorImpl descriptor = descriptors.stream()
                .filter(d -> d.getClass().isAssignableFrom(KubernetesCloud.DescriptorImpl.class))
                .findFirst()
                .orElseGet(KubernetesCloud.DescriptorImpl::new);
        assertThat(
                "Valid url doesn't raise error",
                descriptor.doCheckServerUrl("https://eample.org").getMessage(),
                nullValue());
        assertThat(
                "Invalid url raises error",
                descriptor.doCheckServerUrl("http://eample.org").getMessage(),
                notNullValue());
        assertThat(
                "Valid cert doesn't raise error",
                descriptor.doCheckServerCertificate(getCert("rsa2048")).getMessage(),
                nullValue());
        assertThat(
                "Invalid cert raises error",
                descriptor.doCheckServerCertificate(getCert("rsa1024")).getMessage(),
                notNullValue());
        assertThat(
                "No TLS skip doesn't raise error",
                descriptor.doCheckSkipTlsVerify(false).getMessage(),
                nullValue());
        assertThat(
                "TLS skip raises error", descriptor.doCheckSkipTlsVerify(true).getMessage(), notNullValue());
    }

    private String getCert(String alg) throws IOException {
        return FileUtils.readFileToString(
                Paths.get("src/test/resources/org/csanchez/jenkins/plugins/kubernetes/KubernetesCloudFIPSTest/certs")
                        .resolve(alg)
                        .toFile(),
                Charset.defaultCharset());
    }
}
