/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

/**
 * @author Carlos Sanchez
 *
 */
public class JenkinsRuleNonLocalhost extends JenkinsRule {
    private static final Logger LOGGER = Logger.getLogger(JenkinsRuleNonLocalhost.class.getName());

    private static final String HOST = System.getProperty("connectorHost", "192.168.64.1");

    private Integer port;

    public JenkinsRuleNonLocalhost(Integer port) {
        this.port = port;
    }

    public JenkinsRuleNonLocalhost() {
    }

    /**
     * Prepares a webapp hosting environment to get {@link javax.servlet.ServletContext} implementation
     * that we need for testing.
     */
    protected ServletContext createWebServer() throws Exception {
        server = new Server(new ThreadPoolImpl(new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("Jetty Thread Pool");
                return t;
            }
        })));

        WebAppContext context = new WebAppContext(WarExploder.getExplodedDir().getPath(), contextPath);
        context.setClassLoader(getClass().getClassLoader());
        context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});
        context.addBean(new NoListenerConfiguration(context));
        server.setHandler(context);
        context.setMimeTypes(MIME_TYPES);
        context.getSecurityHandler().setLoginService(configureUserRealm());
        context.setResourceBase(WarExploder.getExplodedDir().getPath());

        ServerConnector connector = new ServerConnector(server);
        HttpConfiguration config = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
        // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
        config.setRequestHeaderSize(12 * 1024);
        connector.setHost(HOST);

        if (System.getProperty("port")!=null) {
            LOGGER.info("Overriding port using system property: " + System.getProperty("port"));
            connector.setPort(Integer.parseInt(System.getProperty("port")));
        } else {
            if (port != null) {
                connector.setPort(port);
            }
        }

        server.addConnector(connector);
        server.start();

        localPort = connector.getLocalPort();
        LOGGER.log(Level.INFO, "Running on {0}", getURL());

        return context.getServletContext();
    }

}
