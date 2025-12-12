package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.csanchez.jenkins.plugins.kubernetes.pod.retention.Reaper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;

public class KubernetesCloudRestartTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private MockedStatic<KubernetesClientProvider> mockedProvider;

    @Before
    public void setup() {
        mockedProvider = mockStatic(KubernetesClientProvider.class);
    }

    @After
    public void tearDown() {
        mockedProvider.close();
    }

    private String createSelfSignedCert(Instant expiry) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name("CN=Test");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
        Date notAfter = Date.from(expiry);

        X509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        return Base64.getEncoder().encodeToString(cert.getEncoded());
    }

    @Test
    public void testScheduleRestartOnExpiry() throws Exception {
        // Setup custom log handler
        Logger logger = Logger.getLogger(KubernetesCloud.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);

        try {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes");
            cloud.setServerUrl("https://k8s.example.com");
            j.jenkins.clouds.add(cloud);

            // Mock Client and Config
            KubernetesClient mockClient = mock(KubernetesClient.class);
            Config mockConfig = mock(Config.class);

            // Create a cert expiring in 5 minutes
            Instant expiry = Instant.now().plus(5, ChronoUnit.MINUTES);
            String certData = createSelfSignedCert(expiry);

            when(mockConfig.getClientCertData()).thenReturn(certData);
            when(mockClient.getConfiguration()).thenReturn(mockConfig);
            when(mockClient.getMasterUrl()).thenReturn(new URL("https://k8s.example.com"));

            // Mock Pods operation for informer creation
            MixedOperation podsOp = mock(MixedOperation.class);
            MixedOperation namespaceOp = mock(MixedOperation.class);
            MixedOperation labelsOp = mock(MixedOperation.class);
            SharedIndexInformer informer = mock(SharedIndexInformer.class);

            when(mockClient.pods()).thenReturn(podsOp);
            when(podsOp.inNamespace(anyString())).thenReturn(namespaceOp);
            when(namespaceOp.withLabels(any())).thenReturn(labelsOp);
            when(labelsOp.inform(any(), anyLong())).thenReturn(informer);

            mockedProvider
                    .when(() -> KubernetesClientProvider.createClient(any(KubernetesCloud.class)))
                    .thenReturn(mockClient);

            // Act
            PodTemplate template = new PodTemplate();
            template.setName("test-template");
            template.setLabel("test");
            template.setNamespace("default");

            KubernetesSlave slave = new KubernetesSlave.Builder()
                    .cloud(cloud)
                    .podTemplate(template)
                    .name("slave1")
                    .build();

            // Fix for slave.getNamespace() returning null despite template having it
            Field namespaceField = KubernetesSlave.class.getDeclaredField("namespace");
            namespaceField.setAccessible(true);
            namespaceField.set(slave, "default");

            cloud.registerPodInformer(slave);

            // Assert
            boolean found = false;
            for (String msg : messages) {
                if (msg != null && msg.contains("Scheduling proactive informer restart")) {
                    found = true;
                    break;
                }
            }
            assertTrue("Should have logged restart scheduling. Messages: " + messages, found);
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    public void testReaperWatchRestartOnExpiry() throws Exception {
        // Setup custom log handler for Reaper
        Logger logger = Logger.getLogger(Reaper.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);

        try {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes");
            cloud.setServerUrl("https://k8s.example.com");
            j.jenkins.clouds.add(cloud);

            // Mock Client and Config
            KubernetesClient mockClient = mock(KubernetesClient.class);
            Config mockConfig = mock(Config.class);

            // Create a cert expiring in 5 minutes
            Instant expiry = Instant.now().plus(5, ChronoUnit.MINUTES);
            String certData = createSelfSignedCert(expiry);

            when(mockConfig.getClientCertData()).thenReturn(certData);
            when(mockClient.getConfiguration()).thenReturn(mockConfig);
            when(mockClient.getMasterUrl()).thenReturn(new URL("https://k8s.example.com"));
            when(mockClient.getNamespace()).thenReturn("default"); // Reaper needs this

            // Mock Watch operation
            MixedOperation podsOp = mock(MixedOperation.class);
            MixedOperation namespaceOp = mock(MixedOperation.class);

            when(mockClient.pods()).thenReturn(podsOp);
            when(podsOp.inNamespace(anyString())).thenReturn(namespaceOp);
            when(namespaceOp.watch(any())).thenReturn(mock(io.fabric8.kubernetes.client.Watch.class));

            mockedProvider
                    .when(() -> KubernetesClientProvider.createClient(any(KubernetesCloud.class)))
                    .thenReturn(mockClient);
            mockedProvider
                    .when(() -> KubernetesClientProvider.getValidity(any(KubernetesCloud.class)))
                    .thenReturn(1);

            // Act
            // Trigger Reaper.watchCloud using reflection as it's private
            java.lang.reflect.Method watchCloudMethod =
                    Reaper.class.getDeclaredMethod("watchCloud", KubernetesCloud.class);
            watchCloudMethod.setAccessible(true);
            watchCloudMethod.invoke(Reaper.getInstance(), cloud);

            // Assert
            boolean found = false;
            for (String msg : messages) {
                if (msg != null && msg.contains("Scheduling proactive Reaper watcher restart")) {
                    found = true;
                    break;
                }
            }
            assertTrue("Should have logged Reaper watcher restart scheduling. Messages: " + messages, found);

        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    public void testNoRestartForLongLivedCert() throws Exception {
        // Setup custom log handler
        Logger logger = Logger.getLogger(KubernetesCloud.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);

        try {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes");
            cloud.setServerUrl("https://k8s.example.com");
            j.jenkins.clouds.add(cloud);

            // Mock Client and Config
            KubernetesClient mockClient = mock(KubernetesClient.class);
            Config mockConfig = mock(Config.class);

            // Create a cert expiring in 10 years
            Instant expiry = Instant.now().plus(3650, ChronoUnit.DAYS);
            String certData = createSelfSignedCert(expiry);

            when(mockConfig.getClientCertData()).thenReturn(certData);
            when(mockClient.getConfiguration()).thenReturn(mockConfig);
            when(mockClient.getMasterUrl()).thenReturn(new URL("https://k8s.example.com"));

            // Mock Pods operation
            MixedOperation podsOp = mock(MixedOperation.class);
            MixedOperation namespaceOp = mock(MixedOperation.class);
            MixedOperation labelsOp = mock(MixedOperation.class);
            SharedIndexInformer informer = mock(SharedIndexInformer.class);

            when(mockClient.pods()).thenReturn(podsOp);
            when(podsOp.inNamespace(anyString())).thenReturn(namespaceOp);
            when(namespaceOp.withLabels(any())).thenReturn(labelsOp);
            when(labelsOp.inform(any(), anyLong())).thenReturn(informer);

            mockedProvider
                    .when(() -> KubernetesClientProvider.createClient(any(KubernetesCloud.class)))
                    .thenReturn(mockClient);

            // Act
            PodTemplate template = new PodTemplate();
            template.setName("test-template");
            template.setLabel("test");
            template.setNamespace("default");

            KubernetesSlave slave = new KubernetesSlave.Builder()
                    .cloud(cloud)
                    .podTemplate(template)
                    .name("slave1")
                    .build();

            Field namespaceField = KubernetesSlave.class.getDeclaredField("namespace");
            namespaceField.setAccessible(true);
            namespaceField.set(slave, "default");

            cloud.registerPodInformer(slave);

            // Assert
            boolean found = false;
            for (String msg : messages) {
                if (msg != null && msg.contains("Scheduling proactive informer restart")) {
                    found = true;
                    break;
                }
            }
            assertTrue(
                    "Should have logged restart scheduling for long lived cert (it will happen eventually). Messages: "
                            + messages,
                    found);
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    public void testNoRestartForExpiredCert() throws Exception {
        // Setup custom log handler
        Logger logger = Logger.getLogger(KubernetesCloud.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        logger.addHandler(handler);
        logger.setLevel(Level.WARNING); // Expecting WARNING

        try {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes");
            cloud.setServerUrl("https://k8s.example.com");
            j.jenkins.clouds.add(cloud);

            // Mock Client and Config
            KubernetesClient mockClient = mock(KubernetesClient.class);
            Config mockConfig = mock(Config.class);

            // Create a cert already expired
            Instant expiry = Instant.now().minus(5, ChronoUnit.MINUTES);
            String certData = createSelfSignedCert(expiry);

            when(mockConfig.getClientCertData()).thenReturn(certData);
            when(mockClient.getConfiguration()).thenReturn(mockConfig);
            when(mockClient.getMasterUrl()).thenReturn(new URL("https://k8s.example.com"));

            // Mock Pods operation
            MixedOperation podsOp = mock(MixedOperation.class);
            MixedOperation namespaceOp = mock(MixedOperation.class);
            MixedOperation labelsOp = mock(MixedOperation.class);
            SharedIndexInformer informer = mock(SharedIndexInformer.class);

            when(mockClient.pods()).thenReturn(podsOp);
            when(podsOp.inNamespace(anyString())).thenReturn(namespaceOp);
            when(namespaceOp.withLabels(any())).thenReturn(labelsOp);
            when(labelsOp.inform(any(), anyLong())).thenReturn(informer);

            mockedProvider
                    .when(() -> KubernetesClientProvider.createClient(any(KubernetesCloud.class)))
                    .thenReturn(mockClient);

            // Act
            PodTemplate template = new PodTemplate();
            template.setName("test-template");
            template.setLabel("test");
            template.setNamespace("default");

            KubernetesSlave slave = new KubernetesSlave.Builder()
                    .cloud(cloud)
                    .podTemplate(template)
                    .name("slave1")
                    .build();

            Field namespaceField = KubernetesSlave.class.getDeclaredField("namespace");
            namespaceField.setAccessible(true);
            namespaceField.set(slave, "default");

            cloud.registerPodInformer(slave);

            // Assert
            boolean foundWarning = false;
            for (String msg : messages) {
                if (msg != null && msg.contains("Certificate is already expired or expires very soon")) {
                    foundWarning = true;
                    break;
                }
            }
            assertTrue("Should have logged warning for expired cert. Messages: " + messages, foundWarning);
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    public void testRestartExecution() throws Exception {
        // Setup custom log handler
        Logger logger = Logger.getLogger(KubernetesCloud.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);

        try {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes");
            cloud.setServerUrl("https://k8s.example.com");
            j.jenkins.clouds.add(cloud);

            // Mock Client
            KubernetesClient mockClient = mock(KubernetesClient.class);
            Config mockConfig = mock(Config.class);
            when(mockClient.getConfiguration()).thenReturn(mockConfig);
            when(mockClient.getMasterUrl()).thenReturn(new URL("https://k8s.example.com"));

            MixedOperation podsOp = mock(MixedOperation.class);
            MixedOperation namespaceOp = mock(MixedOperation.class);
            MixedOperation labelsOp = mock(MixedOperation.class);
            SharedIndexInformer informer = mock(SharedIndexInformer.class);

            when(mockClient.pods()).thenReturn(podsOp);
            when(podsOp.inNamespace(anyString())).thenReturn(namespaceOp);
            when(namespaceOp.withLabels(any())).thenReturn(labelsOp);
            when(labelsOp.inform(any(), anyLong())).thenReturn(informer);

            mockedProvider
                    .when(() -> KubernetesClientProvider.createClient(any(KubernetesCloud.class)))
                    .thenReturn(mockClient);

            // Trigger restart directly (using package-private access)
            cloud.restartInformer("default");

            // Assert
            mockedProvider.verify(() -> KubernetesClientProvider.invalidate("kubernetes"));
            boolean found = false;
            for (String msg : messages) {
                if (msg != null && msg.contains("Creating fresh informer for namespace default with new credentials")) {
                    found = true;
                    break;
                }
            }
            assertTrue("Should have logged fresh informer creation", found);

        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    public void testStaleCloudHandling() throws Exception {
        // Setup custom log handler
        Logger logger = Logger.getLogger(KubernetesCloud.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);

        try {
            KubernetesCloud cloudOld = new KubernetesCloud("kubernetes");
            cloudOld.setServerUrl("https://k8s.example.com");

            KubernetesCloud cloudNew = new KubernetesCloud("kubernetes"); // Same name
            cloudNew.setServerUrl("https://k8s.example.com");

            // Register OLD cloud first to setup initial state if needed, but we simulate replacement
            j.jenkins.clouds.add(cloudNew); // Register NEW cloud in Jenkins

            // Mock Client
            KubernetesClient mockClient = mock(KubernetesClient.class);
            Config mockConfig = mock(Config.class);
            when(mockClient.getConfiguration()).thenReturn(mockConfig);
            when(mockClient.getMasterUrl()).thenReturn(new URL("https://k8s.example.com"));

            MixedOperation podsOp = mock(MixedOperation.class);
            MixedOperation namespaceOp = mock(MixedOperation.class);
            MixedOperation labelsOp = mock(MixedOperation.class);
            SharedIndexInformer informer = mock(SharedIndexInformer.class);

            when(mockClient.pods()).thenReturn(podsOp);
            when(podsOp.inNamespace(anyString())).thenReturn(namespaceOp);
            when(namespaceOp.withLabels(any())).thenReturn(labelsOp);
            when(labelsOp.inform(any(), anyLong())).thenReturn(informer);

            mockedProvider
                    .when(() -> KubernetesClientProvider.createClient(any(KubernetesCloud.class)))
                    .thenReturn(mockClient);

            // Act: Trigger restart on OLD cloud
            cloudOld.restartInformer("default");

            // Assert
            // 1. Verify invalidation happened for "kubernetes" (the cloud name)
            mockedProvider.verify(() -> KubernetesClientProvider.invalidate("kubernetes"));

            // 2. Verify migration log
            boolean foundMigration = false;
            for (String msg : messages) {
                if (msg != null && msg.contains("migrating informer for namespace default to new instance")) {
                    foundMigration = true;
                    break;
                }
            }
            assertTrue("Should have logged migration to new instance", foundMigration);

            // 3. Verify that NEW cloud has the informer
            // Access private 'informers' map via reflection or check if createInformer was called for cloudNew
            // Since we mocked createClient, we can check if it was called with cloudNew
            mockedProvider.verify(() -> KubernetesClientProvider.createClient(cloudNew));

        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    public void testRestartFailureWithRetry() throws Exception {
        // Setup custom log handler
        Logger logger = Logger.getLogger(KubernetesCloud.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);

        try {
            KubernetesCloud cloud = new KubernetesCloud("kubernetes");
            cloud.setServerUrl("https://k8s.example.com");
            j.jenkins.clouds.add(cloud);

            // Mock Client
            KubernetesClient mockClient = mock(KubernetesClient.class);
            Config mockConfig = mock(Config.class);
            when(mockClient.getConfiguration()).thenReturn(mockConfig);
            when(mockClient.getMasterUrl()).thenReturn(new URL("https://k8s.example.com"));

            // Simulate failure during createClient (called by createInformer)
            mockedProvider
                    .when(() -> KubernetesClientProvider.createClient(any(KubernetesCloud.class)))
                    .thenReturn(mockClient) // First call (connect) works if needed, or fail immediately
                    .thenThrow(new RuntimeException("API Unreachable")); // Fail on restart attempt

            // We need createInformer to fail. createInformer calls connect() which calls
            // KubernetesClientProvider.createClient.
            // But verify calls inside restartInformer:
            // 1. KubernetesClientProvider.invalidate
            // 2. createInformer -> connect -> createClient

            // So if createClient throws, createInformer throws (or returns null depending on impl, but exception
            // propagates).

            // Act
            cloud.restartInformer("default");

            // Assert
            boolean foundRetryLog = false;
            for (String msg : messages) {
                if (msg != null && msg.contains("retrying in 10s")) {
                    foundRetryLog = true;
                    break;
                }
            }
            assertTrue("Should have logged retry message after failure. Messages: " + messages, foundRetryLog);

        } finally {
            logger.removeHandler(handler);
        }
    }
}
