package org.csanchez.jenkins.plugins.kubernetes;

public abstract class Utils {
    public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERTIFICATE = "-----END CERTIFICATE-----";
    public static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    public static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

    public static String wrapWithMarker(String begin, String end, String encodedBody) {
        return new StringBuilder(begin).append("\n")
                .append(encodedBody).append("\n")
                .append(end)
                .toString();
    }
}
