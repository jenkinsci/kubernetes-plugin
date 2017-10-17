package org.jvnet.hudson.test;

import groovy.lang.Closure;
import org.junit.rules.MethodRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Scott Hebert
 *
 */
public class RestartableJenkinsNonLocalhostRule extends RestartableJenkinsRule {
    private final int port;

    public RestartableJenkinsNonLocalhostRule(int port) {
        this.port = port;
    }

    @Override
    protected JenkinsRule createJenkinsRule(Description description) {
        return new JenkinsRuleNonLocalhost(port);
    }
}
