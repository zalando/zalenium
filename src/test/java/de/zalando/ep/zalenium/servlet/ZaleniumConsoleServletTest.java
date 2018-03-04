package de.zalando.ep.zalenium.servlet;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.proxy.BrowserStackRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import de.zalando.ep.zalenium.proxy.SauceLabsRemoteProxy;
import de.zalando.ep.zalenium.proxy.TestingBotRemoteProxy;
import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.Hub;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ZaleniumConsoleServletTest {
    private GridRegistry registry;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Supplier<ContainerClient> originalContainerClient;

    @Before
    public void setUp() throws IOException {
        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
        }
        registry = ZaleniumRegistry.newInstance(new Hub(new GridHubConfiguration()));
        
        this.originalContainerClient = ContainerFactory.getContainerClientGenerator();
        ContainerFactory.setContainerClientGenerator(DockerContainerMock::getMockedDockerContainerClient);

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest registrationRequest = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
        DockerSeleniumStarterRemoteProxy proxyZero = DockerSeleniumStarterRemoteProxy.getNewInstance(registrationRequest, registry);

        registrationRequest = TestUtils.getRegistrationRequestForTesting(30001, SauceLabsRemoteProxy.class.getCanonicalName());
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString(), anyString(), anyString())).thenReturn(null);
        SauceLabsRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        SauceLabsRemoteProxy sauceLabsProxy = SauceLabsRemoteProxy.getNewInstance(registrationRequest, registry);

        registrationRequest = TestUtils.getRegistrationRequestForTesting(30002, BrowserStackRemoteProxy.class.getCanonicalName());
        BrowserStackRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        BrowserStackRemoteProxy browserStackRemoteProxy = BrowserStackRemoteProxy.getNewInstance(registrationRequest, registry);

        registrationRequest = TestUtils.getRegistrationRequestForTesting(30003, TestingBotRemoteProxy.class.getCanonicalName());
        TestingBotRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        TestingBotRemoteProxy testingBotRemoteProxy = TestingBotRemoteProxy.getNewInstance(registrationRequest, registry);

        registrationRequest = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getConfiguration().capabilities.clear();
        registrationRequest.getConfiguration().capabilities.addAll(DockerSeleniumStarterRemoteProxy.getCapabilities());

        DockerSeleniumRemoteProxy proxyOne = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);
        registrationRequest = TestUtils.getRegistrationRequestForTesting(40001,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getConfiguration().capabilities.clear();
        List<MutableCapabilities> capabilities = DockerSeleniumStarterRemoteProxy.getCapabilities();
        MutableCapabilities desiredCapabilities = new MutableCapabilities();
        desiredCapabilities.setCapability(CapabilityType.BROWSER_NAME, "NEW_BROWSER");
        desiredCapabilities.setCapability(CapabilityType.PLATFORM_NAME, Platform.LINUX);
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        capabilities.add(desiredCapabilities);
        registrationRequest.getConfiguration().capabilities.addAll(capabilities);

        DockerSeleniumRemoteProxy proxyTwo = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);

        registry.add(proxyZero);
        registry.add(proxyOne);
        registry.add(proxyTwo);
        registry.add(sauceLabsProxy);
        registry.add(browserStackRemoteProxy);
        registry.add(testingBotRemoteProxy);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        when(request.getParameter("config")).thenReturn("true");
        when(request.getParameter("configDebug")).thenReturn("true");
        when(request.getServerName()).thenReturn("localhost");
        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
    }


    @Test
    public void addedNodesAreRenderedInServlet() throws IOException {

        ZaleniumConsoleServlet zaleniumConsoleServlet = new ZaleniumConsoleServlet(registry);

        zaleniumConsoleServlet.doPost(request, response);

        String responseContent = response.getOutputStream().toString();
        System.out.println(responseContent);
        assertThat(responseContent, containsString("Grid Console"));
        assertThat(responseContent, containsString("DockerSeleniumStarterRemoteProxy"));
        assertThat(responseContent, containsString("DockerSeleniumRemoteProxy"));
        assertThat(responseContent, containsString("SauceLabsRemoteProxy"));
    }

    @Test
    public void postAndGetReturnSameContent() throws IOException {

        ZaleniumConsoleServlet zaleniumConsoleServlet = new ZaleniumConsoleServlet(registry);

        zaleniumConsoleServlet.doPost(request, response);
        String postResponseContent = response.getOutputStream().toString();

        zaleniumConsoleServlet.doGet(request, response);
        String getResponseContent = response.getOutputStream().toString();
        assertThat(getResponseContent, containsString(postResponseContent));
    }

    @Test
    public void checkResourcesInConsoleServlet() throws IOException {
        HttpServletRequest httpServletRequest;
        HttpServletResponse httpServletResponse;

        ZaleniumResourceServlet zaleniumResourceServlet = new ZaleniumResourceServlet();

        httpServletRequest = mock(HttpServletRequest.class);
        httpServletResponse = mock(HttpServletResponse.class);
        when(httpServletRequest.getServerName()).thenReturn("localhost");
        when(httpServletRequest.getServletPath()).thenReturn("http://localhost:4444/grid/admin/ZaleniumResourceServlet");
        when(httpServletRequest.getPathInfo()).thenReturn("http://localhost:4444/grid/admin/ZaleniumResourceServlet/images/saucelabs.png");
        when(httpServletResponse.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());

        zaleniumResourceServlet.doGet(httpServletRequest, httpServletResponse);
        assertThat(httpServletResponse.getOutputStream().toString(), containsString("PNG"));
    }
    
    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"https://ondemand.saucelabs.com:443\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://hub-cloud.browserstack.com:80\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://hub.testingbot.com:80\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40000\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40001\"");
        new JMXHelper().unregister(objectName);
        ContainerFactory.setContainerClientGenerator(originalContainerClient);
    }
}
