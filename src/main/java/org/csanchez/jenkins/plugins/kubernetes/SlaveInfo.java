package org.csanchez.jenkins.plugins.kubernetes;

import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class SlaveInfo {

    private final String nodeName;
    private final String computerName;
    private final String computerUrl;
    private final String computerJnlpMac;

    public SlaveInfo(String nodeName) {
        this(nodeName, null, null, null);
    }

    public SlaveInfo(String nodeName, String computerName, String computerUrl, String computerJnlpMac) {
        this.nodeName = nodeName;
        this.computerName = computerName;
        this.computerUrl = computerUrl;
        this.computerJnlpMac = computerJnlpMac;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getComputerName() {
        return computerName;
    }

    public String getComputerUrl() {
        return computerUrl;
    }

    public String getComputerJnlpMac() {
        return computerJnlpMac;
    }

    public boolean isComputerDataPresent() {
        return isNotBlank(computerName) && isNotBlank(computerUrl) && isNotBlank(computerJnlpMac);
    }

    @Override
    public String toString() {
        return "SlaveInfo{" +
                "nodeName='" + nodeName + '\'' +
                ", computerName='" + computerName + '\'' +
                ", computerUrl='" + computerUrl + '\'' +
                ", computerJnlpMac='" + computerJnlpMac + '\'' +
                '}';
    }
}
