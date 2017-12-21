package de.zalando.ep.zalenium.servlet;


import de.zalando.ep.zalenium.registry.ZaleniumRegistry;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.TestUtils;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.Hub;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

public class LiveNodeServletTest {

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
        RegistrationRequest registrationRequest = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getConfiguration().capabilities.clear();
        registrationRequest.getConfiguration().capabilities.addAll(DockerSeleniumStarterRemoteProxy.getCapabilities());
        DockerSeleniumRemoteProxy proxyOne = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);

        registrationRequest = TestUtils.getRegistrationRequestForTesting(40001,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getConfiguration().capabilities.clear();
        registrationRequest.getConfiguration().capabilities.addAll(DockerSeleniumStarterRemoteProxy.getCapabilities());
        DockerSeleniumRemoteProxy proxyTwo = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);

        registry.add(proxyOne);
        registry.add(proxyTwo);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        when(request.getParameter("refresh")).thenReturn("1");
        when(request.getServerName()).thenReturn("localhost");
        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
    }

    @Test
    public void addedNodesAreRenderedInServlet() throws IOException {

        LivePreviewServlet livePreviewServletServlet = new LivePreviewServlet(registry);

        livePreviewServletServlet.doPost(request, response);

        String responseContent = response.getOutputStream().toString();
        assertThat(responseContent, containsString("Zalenium Live Preview"));
        assertThat(responseContent, containsString("http://localhost:40000"));
        assertThat(responseContent, containsString("http://localhost:40001"));
        assertThat(responseContent, containsString("/vnc/host/localhost/port/50000/?nginx=localhost:50000&view_only=true'"));
        assertThat(responseContent, containsString("/vnc/host/localhost/port/50000/?nginx=localhost:50000&view_only=false'"));
        assertThat(responseContent, containsString("/vnc/host/localhost/port/50001/?nginx=localhost:50001&view_only=true'"));
        assertThat(responseContent, containsString("/vnc/host/localhost/port/50001/?nginx=localhost:50001&view_only=false'"));
    }

    @Test
    public void postAndGetReturnSameContent() throws IOException {

        LivePreviewServlet livePreviewServletServlet = new LivePreviewServlet(registry);

        livePreviewServletServlet.doPost(request, response);
        String postResponseContent = response.getOutputStream().toString();

        livePreviewServletServlet.doGet(request, response);
        String getResponseContent = response.getOutputStream().toString();
        assertThat(getResponseContent, containsString(postResponseContent));
    }

    @Test
    public void noRefreshInHtmlWhenParameterIsInvalid() throws IOException {
        when(request.getParameter("refresh")).thenReturn("XYZ");

        LivePreviewServlet livePreviewServletServlet = new LivePreviewServlet(registry);

        livePreviewServletServlet.doPost(request, response);
        String postResponseContent = response.getOutputStream().toString();
        assertThat(postResponseContent, containsString("<meta http-equiv='refresh' content='XYZ' />"));
    }
    
    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40000\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40001\"");
        new JMXHelper().unregister(objectName);
        ContainerFactory.setContainerClientGenerator(originalContainerClient);
    }

}
