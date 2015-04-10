package org.csanchez.jenkins.plugins.kubernetes;

import hudson.Plugin;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Carlos Sanchez carlos@apache.org
 */
public class PluginImpl extends Plugin {

    private static final Logger logger = LoggerFactory.getLogger(PluginImpl.class);

    /**
     * What to call this plug-in to humans.
     */
    public static final String DISPLAY_NAME = "Kubernetes Plugin";

    private static PluginImpl instance;

    /**
     * the default server name.
     */
    public static final String DEFAULT_SERVER_NAME = "defaultServer";

    /**
     * Constructor.
     */
    public PluginImpl() {
        instance = this;
    }

    /**
     * Returns this singleton instance.
     *
     * @return the singleton.
     */
    public static PluginImpl getInstance() {
        return instance;
    }

    @Override
    public void start() throws Exception {
        super.start();
    }

    @Override
    public void load() throws IOException {
        super.load();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
