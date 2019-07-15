package de.zalando.ep.zalenium.servlet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.ProxySet;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.Hub;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import de.zalando.ep.zalenium.util.TestUtils;
import de.zalando.ep.zalenium.util.ZaleniumConfiguration;

public class GridStatusServletTest {

    private HttpServletRequest request;

    private HttpServletResponse response;

    private GridStatusServlet gridStatusServlet;

    private ZaleniumRegistry registry;

    @Before
    public void initMocksAndService() throws IOException {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
        registry = (ZaleniumRegistry) ZaleniumRegistry.newInstance(new Hub(new GridHubConfiguration()), new ProxySet(false));
        gridStatusServlet = new GridStatusServlet(registry);
    }

    /**
     * Check when grid has full capacity status.
     * @throws IOException
     */
    @Test
    public void statusFullCapacity() throws IOException {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);

        RegistrationRequest req = TestUtils.getRegistrationRequestForTesting(40000, DockerSeleniumRemoteProxy.class.getCanonicalName());
        req.getConfiguration().capabilities.clear();
        req.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());
        DockerSeleniumRemoteProxy p1 = new DockerSeleniumRemoteProxy(req, registry);

        try {
            ZaleniumConfiguration.setDesiredContainersOnStartup(2);
            registry.add(p1);
            gridStatusServlet.doGet(request, response);
            Assert.assertThat(response.getOutputStream().toString(), Matchers.containsString("AVAILABLE"));
        } finally {
            registry.stop();
        }
    }

    /**
     * Check when grid has available status.
     * @throws IOException
     */
    @Test
    public void statusAvailable() throws IOException {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);

        RegistrationRequest req = TestUtils.getRegistrationRequestForTesting(40000, DockerSeleniumRemoteProxy.class.getCanonicalName());
        req.getConfiguration().capabilities.clear();
        req.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());
        DockerSeleniumRemoteProxy p1 = new DockerSeleniumRemoteProxy(req, registry);

        try {
            ZaleniumConfiguration.setDesiredContainersOnStartup(1);
            registry.add(p1);
            gridStatusServlet.doGet(request, response);
            Assert.assertThat(response.getOutputStream().toString(), Matchers.containsString("FULL_CAPACITY"));
        } finally {
            registry.stop();
        }
    }

    /**
     * Check when grid has unavailable status.
     * @throws IOException
     */
    @Test
    public void statusUnavailable() throws IOException {
        try {
            ZaleniumConfiguration.setDesiredContainersOnStartup(2);
            gridStatusServlet.doGet(request, response);
            Assert.assertThat(response.getOutputStream().toString(), Matchers.containsString("UNAVAILABLE"));
        } finally {
            registry.stop();
        }
    }

    @Test(expected = RuntimeException.class)
    public void nullRegistry() throws IOException {
        GridStatusServlet gridStatusServletWithRegistryNull = new GridStatusServlet();
        gridStatusServletWithRegistryNull.doGet(request, response);
    }

}
