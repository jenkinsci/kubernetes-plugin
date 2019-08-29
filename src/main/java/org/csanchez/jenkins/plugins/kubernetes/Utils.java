package org.csanchez.jenkins.plugins.kubernetes;

public abstract class Utils {

    public static String wrapWithMarker(String begin, String end, String encodedBody) {
        return new StringBuilder(begin).append("\n")
            .append(encodedBody).append("\n")
            .append(end)
            .toString();
    }

    public static String wrapCertificate(String certData) {
        String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
        String END_CERTIFICATE = "-----END CERTIFICATE-----";
        if (!certData.startsWith(BEGIN_CERTIFICATE)) {
            return wrapWithMarker(BEGIN_CERTIFICATE, END_CERTIFICATE, certData);
        }
        return certData;
    }

    public static String wrapPrivateKey(String keyData) {
        String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
        String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";
        if (!keyData.startsWith(BEGIN_PRIVATE_KEY)) {
            return wrapWithMarker(BEGIN_PRIVATE_KEY, END_PRIVATE_KEY, keyData);
        }
        return keyData;
    }
}
