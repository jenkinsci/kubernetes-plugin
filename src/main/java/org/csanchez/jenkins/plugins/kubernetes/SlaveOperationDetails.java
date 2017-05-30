package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Slave;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class SlaveOperationDetails {

    private final Slave slave;
    private final String slaveName;
    private int secondsSpent;

    public SlaveOperationDetails(Slave slave) {
        this(slave, null);
    }

    public SlaveOperationDetails(Slave slave, String slaveName) {
        this.slave = slave;
        this.slaveName = slaveName;
    }

    public Slave getSlave() {
        return slave;
    }

    public String getSlaveName() {
        return slaveName;
    }

    public int getSecondsSpent() {
        return secondsSpent;
    }

    public void setSecondsSpent(int secondsSpent) {
        this.secondsSpent = secondsSpent;
    }
}
