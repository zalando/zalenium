package de.zalando.ep.zalenium.servlet;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
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
import org.openqa.grid.internal.GridRegistry;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

@SuppressWarnings("Duplicates")
public class LiveNodeServletTest {

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

        DockerSeleniumRemoteProxy p1 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine1:4444/", registry);
        DockerSeleniumRemoteProxy p2 = TestUtils.getNewBasicRemoteProxy("app1", "http://machine2:4444/", registry);

        registry.add(p1);
        registry.add(p2);

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
        assertThat(responseContent, containsString("http://machine1:4444"));
        assertThat(responseContent, containsString("http://machine2:4444"));
        assertThat(responseContent, containsString("/vnc/host/machine1/port/40000/?nginx=machine1:40000&view_only=true'"));
        assertThat(responseContent, containsString("/vnc/host/machine1/port/40000/?nginx=machine1:40000&view_only=false'"));
        assertThat(responseContent, containsString("/vnc/host/machine2/port/40000/?nginx=machine2:40000&view_only=true'"));
        assertThat(responseContent, containsString("/vnc/host/machine2/port/40000/?nginx=machine2:40000&view_only=false'"));
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
        ContainerFactory.setDockerContainerClient(originalContainerClient);
    }
    
}
