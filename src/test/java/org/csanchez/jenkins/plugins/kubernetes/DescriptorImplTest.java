package org.csanchez.jenkins.plugins.kubernetes;

import com.github.kubernetes.java.client.interfaces.KubernetesAPIClientInterface;
import com.github.kubernetes.java.client.v2.KubernetesApiClient;
import com.github.kubernetes.java.client.v2.RestFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DescriptorImplTest {

    private Server server;
    private int port;

    @Before
    public void setUp() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        server = new Server(0);
        ServletContextHandler servletContext = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        servletContext.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                final String requestURI = req.getRequestURI();
                assertThat(req.getHeader("Accept"), is("application/json"));
                assertThat(req.getHeader("Authorization"), is("Basic " + "dXNlcjpzZWtyaXQ="));
                assertThat(requestURI, is("/api/v1beta2/pods"));
            }
        }), "/*");
        servletContext.addLifeCycleListener(new ServletListenerAdapter() {
            @Override
            public void lifeCycleStarted(LifeCycle lifeCycle) {
                latch.countDown();
            }
        });
        server.setHandler(servletContext);
        server.start();
        latch.await();
        port = server.getConnectors()[0].getLocalPort();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void testDoTestConnection() throws Exception {
        RestFactory factory = new RestFactory(KubernetesCloud.class.getClassLoader());
        KubernetesAPIClientInterface client = new KubernetesApiClient("http://localhost:" + port, "user", "sekrit", factory);
        client.getAllPods();
    }

    class ServletListenerAdapter implements LifeCycle.Listener {


        @Override
        public void lifeCycleStarting(LifeCycle lifeCycle) {

        }

        @Override
        public void lifeCycleStarted(LifeCycle lifeCycle) {

        }

        @Override
        public void lifeCycleFailure(LifeCycle lifeCycle, Throwable throwable) {

        }

        @Override
        public void lifeCycleStopping(LifeCycle lifeCycle) {

        }

        @Override
        public void lifeCycleStopped(LifeCycle lifeCycle) {

        }
    }
}