package de.zalando.ep.zalenium.servlet;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

@SuppressWarnings("Duplicates")
public class VncAuthenticationServletTest {
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

        // Creating the configuration and the registration request of the proxy (node)
        DockerSeleniumRemoteProxy proxyOne = TestUtils.getNewBasicRemoteProxy("app1", "http://machine1:4444/", registry);
        DockerSeleniumRemoteProxy proxyTwo = TestUtils.getNewBasicRemoteProxy("app1", "http://machine2:4444/", registry);

        registry.add(proxyOne);
        registry.add(proxyTwo);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        when(request.getParameter("refresh")).thenReturn("1");
        when(request.getServerName()).thenReturn("localhost");
        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
    }

    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40001\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40000\"");
        new JMXHelper().unregister(objectName);
        ContainerFactory.setDockerContainerClient(originalContainerClient);
    }

    @Test
    public void testAuthenticationSucceedsForNoVnc() {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/machine1/port/40000/?nginx=machine1:40000&view_only=true");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(200));
    }
    
    @Test
    public void testAuthenticationSucceedsForWebsockify() {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/proxy/machine1:40000/websockify");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(200));
    }
    
    @Test
    public void testAuthenticationFailsForWebsockify() {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/proxy/not_a_machine:40000/websockify");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }
    
    @Test
    public void testAuthenticationFailsForVncWithBadPort() {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/machine1/port/50003/?nginx=machine1:50003&view_only=true");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }
    
    @Test
    public void testAuthenticationFailsForVncWithBadHost() {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/fakehost/port/40000/?nginx=fakehost:40000&view_only=true");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }

    @Test
    public void testAuthenticationFailsWithNoHeader() {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }
}
