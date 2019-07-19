package de.zalando.ep.zalenium.servlet;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.utils.configuration.GridNodeConfiguration;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.matcher.ZaleniumCapabilityType;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

public class LiveNodeServletFilterActiveSessions {

    private LivePreviewServlet livePreviewServletServlet;

    private HttpServletRequest request;

    private HttpServletResponse response;

    /**
     * Test filter just active sessions in admin live view.
     * @throws IOException
     */
    @Test
    public void filterNodesAreRenderedInServlet() throws IOException {
        when(request.getParameter("only_active_sessions")).thenReturn("true");

        checkNodesAreRendered(not(containsString("http://machine2:4444")));
    }

    /**
     * Test with no filter for active sessions in admin live view.
     * @throws IOException
     */
    @Test
    public void noFilterNodesAreRenderedInServlet() throws IOException {
        when(request.getParameter("only_active_sessions")).thenReturn("false");

        checkNodesAreRendered(containsString("http://machine2:4444"));
    }

    /**
     * Test with bad param filter value for active sessions in admin live view.
     * @throws IOException
     */
    @Test
    public void badValueForilterNodesAreRenderedInServlet() throws IOException {
        when(request.getParameter("only_active_sessions")).thenReturn("xxx");

        checkNodesAreRendered(containsString("http://machine2:4444"));
    }

    @Before
    public void setup() throws IOException {
        if (livePreviewServletServlet == null) {
            try {
                ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
                ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
                new JMXHelper().unregister(objectName);
            } catch (MalformedObjectNameException | InstanceNotFoundException e) {
                // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
            }
            GridRegistry registry = new SimpleRegistry();

            ContainerFactory.setDockerContainerClient(DockerContainerMock::getRegisterOnlyDockerContainerClient);

            DockerSeleniumRemoteProxy p1 = getNewBasicRemoteProxy("app1", "http://machine1:4444/", registry);
            DockerSeleniumRemoteProxy p2 = getNewBasicRemoteProxy("app1", "http://machine2:4444/", registry);

            registry.add(p1);
            registry.add(p2);

            livePreviewServletServlet = new LivePreviewServlet(registry);

            Map<String, Object> requestedCapability = new HashMap<>();
            requestedCapability.put(CapabilityType.BROWSER_NAME, "app1");
            requestedCapability.put(CapabilityType.BROWSER_VERSION, "3");
            requestedCapability.put(CapabilityType.PLATFORM_NAME, "LINUX");
            requestedCapability.put(CapabilityType.APPLICATION_NAME, "app");
            requestedCapability.put(ZaleniumCapabilityType.SCREEN_RESOLUTION, ZaleniumCapabilityType.SCREEN_RESOLUTION_DASH);
            requestedCapability.put(ZaleniumCapabilityType.TIME_ZONE, "N/A");

            p1.getNewSession(requestedCapability);

            request = mock(HttpServletRequest.class);
            response = mock(HttpServletResponse.class);

            when(request.getServerName()).thenReturn("localhost");
            when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());

        }
    }

    private void checkNodesAreRendered(Matcher<String> stringMatcher) throws IOException {
        livePreviewServletServlet.doPost(request, response);

        String responseContent = response.getOutputStream().toString();
        assertThat(responseContent, containsString("http://machine1:4444"));
        assertThat(responseContent, stringMatcher);
    }

    private DockerSeleniumRemoteProxy getNewBasicRemoteProxy(String browser, String url, GridRegistry registry) throws MalformedURLException {

        GridNodeConfiguration config = new GridNodeConfiguration();
        URL u = new URL(url);
        config.host = u.getHost();
        config.port = u.getPort();
        config.role = "webdriver";
        RegistrationRequest req = RegistrationRequest.build(config);
        req.getConfiguration().capabilities.clear();

        DesiredCapabilities capability = new DesiredCapabilities();
        capability.setBrowserName(browser);
        capability.setCapability(CapabilityType.BROWSER_VERSION, "3");
        capability.setCapability(CapabilityType.PLATFORM_NAME, "LINUX");
        capability.setCapability(CapabilityType.APPLICATION_NAME, "app");
        capability.setCapability(ZaleniumCapabilityType.SCREEN_RESOLUTION, ZaleniumCapabilityType.SCREEN_RESOLUTION_DASH);
        capability.setCapability(ZaleniumCapabilityType.TIME_ZONE, "N/A");
        req.getConfiguration().capabilities.add(capability);

        return new DockerSeleniumRemoteProxy(req, registry);

    }
}
