package de.zalando.ep.zalenium.servlet;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.zalando.ep.zalenium.container.DockerContainerClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.proxy.BrowserStackRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.SauceLabsRemoteProxy;
import de.zalando.ep.zalenium.proxy.TestingBotRemoteProxy;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

public class ZaleniumConsoleServletTest {
    private GridRegistry registry;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private DockerContainerClient originalContainerClient;

    @Before
    public void setUp() throws IOException {
        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
        }
        registry = new SimpleRegistry();

        this.originalContainerClient = ContainerFactory.getDockerContainerClient();
        ContainerFactory.setDockerContainerClient(DockerContainerMock.getRegisterOnlyDockerContainerClient());

        RegistrationRequest registrationRequest = TestUtils.getRegistrationRequestForTesting(30001, SauceLabsRemoteProxy.class.getCanonicalName());
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

        DockerSeleniumRemoteProxy proxyOne = TestUtils.getNewBasicRemoteProxy("app1", "http://machine1:4444/", registry);
        DockerSeleniumRemoteProxy proxyTwo = TestUtils.getNewBasicRemoteProxy("app1", "http://machine2:4444/", registry);

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
        ContainerFactory.setDockerContainerClient(originalContainerClient);
    }
}
