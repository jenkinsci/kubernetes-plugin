package org.csanchez.jenkins.plugins.kubernetes.pipeline.kubectl;

import org.csanchez.jenkins.plugins.kubernetes.OpenShiftTokenCredentialImpl;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl;
import com.cloudbees.plugins.credentials.impl.CertificateCredentialsImpl.FileOnMasterKeyStoreSource;

import hudson.FilePath;
import hudson.model.Result;
import hudson.util.Secret;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import java.util.Base64;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import static org.csanchez.jenkins.plugins.kubernetes.KubernetesTestUtil.*;
import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.apache.commons.compress.utils.IOUtils;
import org.jvnet.hudson.test.JenkinsRuleNonLocalhost;

public class KubectlBuildWrapperStepTest {
    protected static final String CREDENTIAL_ID = "cred1234";
    protected static final String PASSPHRASE = "test";
    protected static final String USERNAME = "bob";
    protected static final String PASSWORD = "s3cr3t";
    protected static final String CA_CERTIFICATE = "-----BEGIN CERTIFICATE-----\na-certificate\n-----END CERTIFICATE-----";
    protected static final String SERVER_URL = "https://localhost:6443";

    @Rule
    public JenkinsRuleNonLocalhost r = new JenkinsRuleNonLocalhost();

    @BeforeClass
    public static void isKubernetesConfigured() throws Exception {
        assumeKubernetes();
    }

    protected String loadPipelineScript(String name) {
        try {
            return new String(IOUtils.toByteArray(getClass().getResourceAsStream(name)));
        } catch (Throwable t) {
            throw new RuntimeException("Could not read resource:[" + name + "].");
        }
    }

    protected BaseStandardCredentials usernamePasswordCredential(){
      return new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, CREDENTIAL_ID, "sample", USERNAME, PASSWORD);
    }

    protected BaseStandardCredentials tokenCredential(){
      return new OpenShiftTokenCredentialImpl(CredentialsScope.GLOBAL, CREDENTIAL_ID, "sample", Secret.fromString(PASSWORD));
    }

    protected BaseStandardCredentials certificateCredential(){
      String storeFile = getClass().getResource("/org/csanchez/jenkins/plugins/kubernetes/kubernetes.pkcs12").getFile();
      CertificateCredentialsImpl.KeyStoreSource keyStoreSource = new FileOnMasterKeyStoreSource(storeFile);
      return new CertificateCredentialsImpl(CredentialsScope.GLOBAL, CREDENTIAL_ID, "sample", PASSPHRASE, keyStoreSource);
    }

    @Test public void testBasicWithCa() throws Exception {
        String encodedCertificate = new String(Base64.getEncoder().encode(CA_CERTIFICATE.getBytes()));
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), usernamePasswordCredential());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("kubectlWithCa.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        r.assertLogContains("kubectl configuration cleaned up", b);

        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();
        assertTrue(configDumpContent.contains("certificate-authority-data: " + encodedCertificate));
        assertTrue(configDumpContent.contains("server: " + SERVER_URL));
    }

    @Test public void testBasicWithoutCa() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), usernamePasswordCredential());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("kubectlWithoutCa.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        r.assertLogContains("kubectl configuration cleaned up", b);
        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();
        assertTrue(configDumpContent.contains("insecure-skip-tls-verify: true"));
        assertTrue(configDumpContent.contains("server: " + SERVER_URL));
    }

    @Test public void testUsernamePasswordCredentials() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), usernamePasswordCredential());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("kubectlWithoutCa.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogNotContains(PASSWORD, b);

        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();
        assertTrue(configDumpContent.contains("username: " + USERNAME));
        assertTrue(configDumpContent.contains("password: " + PASSWORD));
    }

    @Test public void testTokenCredentials() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), tokenCredential());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("kubectlWithoutCa.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogNotContains(PASSWORD, b);

        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();
        assertTrue(configDumpContent.contains("token: " + PASSWORD));
    }

    @Test public void testCertificateCredentials() throws Exception {
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), certificateCredential());

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(loadPipelineScript("kubectlWithoutCa.groovy"), true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        assertNotNull(b);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        FilePath configDump = r.jenkins.getWorkspaceFor(p).child("configDump");
        assertTrue(configDump.exists());
        String configDumpContent = configDump.readToString().trim();
        assertTrue(configDumpContent.contains("client-certificate-data: " +
              "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUNhekNDQWRRQ0NRR" +
              "FZ0VnhhSHZxcXR6QU5CZ2txaGtpRzl3MEJBUVVGQURCNk1Rc3dDUVlEVl" +
              "FRR0V3SkJWVEVUTUJFR0ExVUVDQk1LVTI5dFpTMVRkR0YwWlRFUU1BNEd" +
              "BMVVFQ2hNSFNtVnVhMmx1Y3pFYU1CZ0dBMVVFQXhNUlMzVmlaWEp1WlhS" +
              "bGN5MVZjMlZ5TFRFeEtEQW1CZ2txaGtpRzl3MEJDUUVXR1d0MVltVnlib" +
              "VYwWlhNdGRYTmxjaTB4UUdwbGJtdHBibk13SGhjTk1UY3hNREF6TVRJMU" +
              "56VTVXaGNOTVRneE1EQXpNVEkxTnpVNVdqQjZNUXN3Q1FZRFZRUUdFd0p" +
              "CVlRFVE1CRUdBMVVFQ0JNS1UyOXRaUzFUZEdGMFpURVFNQTRHQTFVRUNo" +
              "TUhTbVZ1YTJsdWN6RWFNQmdHQTFVRUF4TVJTM1ZpWlhKdVpYUmxjeTFWY" +
              "zJWeUxURXhLREFtQmdrcWhraUc5dzBCQ1FFV0dXdDFZbVZ5Ym1WMFpYTX" +
              "RkWE5sY2kweFFHcGxibXRwYm5Nd2daOHdEUVlKS29aSWh2Y05BUUVCQlF" +
              "BRGdZMEFNSUdKQW9HQkFMS0ViejIrbGpwN3dNTEZYckdhVEZ4M25HUUE0" +
              "c1dsWGtLcGdqYjYrd1U3ZTdYVDFuOHFoOGpEeVNITDRHVUp1TjVUTUNON" +
              "TZOQ3g3Y013SHdYZmRyUlhHZFB0UkxZcUdBSStENnFZWlRsQzhzSFNyTF" +
              "ZXU1ZZQ01IaElIZEZ6QmxJN2t3RVh2RW1JcVIvMVJXS2dHMG1sQnhpQjV" +
              "mbmxXbmphME9UdDRpY2hBZ01CQUFFd0RRWUpLb1pJaHZjTkFRRUZCUUFE" +
              "Z1lFQUZIdktxTU5vdStpZE5aQ2FKSjZ4MnUweHJreEJHMDFVYnNteHlWd" +
              "1Q1dWlDck96c3cveGk5SVc0dmpGRmtKZXpNMlJxc0NHaEZvRFA0aTY0U0" +
              "srK0NYbXJ6VVJ4UUpJYi9xeEdqRUM4SDR5QVU2dGs3YStoellYVWt4bnZ" +
              "sK0F5OWc5WnBWR3Z5a1krbHlGNEJkdnlYZ2I5aGVBbGp3azRtdHRoNmdV" +
              "eXdaRT0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ=="));
        assertTrue(configDumpContent.contains("client-key-data: " +
              "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUNkUUlCQURBTkJna" +
              "3Foa2lHOXcwQkFRRUZBQVNDQWw4d2dnSmJBZ0VBQW9HQkFMS0ViejIrbG" +
              "pwN3dNTEZYckdhVEZ4M25HUUE0c1dsWGtLcGdqYjYrd1U3ZTdYVDFuOHF" +
              "oOGpEeVNITDRHVUp1TjVUTUNONTZOQ3g3Y013SHdYZmRyUlhHZFB0UkxZ" +
              "cUdBSStENnFZWlRsQzhzSFNyTFZXU1ZZQ01IaElIZEZ6QmxJN2t3RVh2R" +
              "W1JcVIvMVJXS2dHMG1sQnhpQjVmbmxXbmphME9UdDRpY2hBZ01CQUFFQ2" +
              "dZQUdFN29SdVFZMk1YWkxheHFoSXlhTVUwb1FvWE1XMVYxVEdhQWtMUUV" +
              "VbVlUSm1NK0pmckltcEh1WldlNW1vaUVYK0c4QUZpdFZ4MmpYcHpDM0sz" +
              "ZEg5OEZCOXJrcmZGamJaWEpQOG1kaHVUUXo1eVEwVkZ5c1gvRStzZi9Zd" +
              "E5sNjNxd2dDQU1POEU4TkRYUnA3NDFwTWpyRXA2cHk1d1JWRHo3aDdnY3" +
              "dBUUpCQU9kNExXSjlpQ09DOWpQQmR1QVZXcVJsMW81b3dDR3RWcHlCV25" +
              "OMHZENFZRMk5ZZko2WVBWYXZreDJMU1p6eEdGMzllWDFCemRFVVEvTHJR" +
              "WThINXFFQ1FRREZiNmM3bG1Od09ZSXh6OUlhWWZ3b0krblpwMFpFTnUrY" +
              "k14M3EyL01NRWdYREhhS2l5Sm1peGFTbTUvT2IybHVMcVFTRTZvKzluUS" +
              "twWGQ5a3NQQ0JBa0FMQ21wdnhqa1dLSXNCMFBxUW1iUW5IMHhxb29oM2t" +
              "zTU0yQWF1ZHlUN2VSd3J3dTYreWRnektGREdHZnk2NWEwWjNwdEs1RGFq" +
              "QUdwMVRjOWt1U1hCQWtBU29UNStlT3BaSkpRTWJ6ZThGWkxkbHNYeUs3N" +
              "k5vVUZxdTZBUEVVSVYyWDJCczhJczZoRFZNeUVlUHJUVjkveTdhTzlzTz" +
              "FYazVuVWIzaWUrTUpRQkFrQngrNWRWTHh1UVJ3YUZVOTJsQ2syR2p5Rk9" +
              "XN0MvMk55bFlKUldlNDd1NkRqOCt6R0NPblZFaGlNQlpJMHppbWdRWDlV" +
              "aHVkT1NSQis5YzRYWFNFTzUKLS0tLS1FTkQgUFJJVkFURSBLRVktLS0tLQ=="));

    }
}
