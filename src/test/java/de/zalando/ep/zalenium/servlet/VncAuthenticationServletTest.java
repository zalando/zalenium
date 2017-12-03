package de.zalando.ep.zalenium.servlet;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.function.Supplier;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.DefaultGridRegistry;
import org.openqa.grid.internal.GridRegistry;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.ep.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import de.zalando.ep.zalenium.util.DockerContainerMock;
import de.zalando.ep.zalenium.util.TestUtils;

public class VncAuthenticationServletTest {
    private GridRegistry registry;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Supplier<ContainerClient> originalContainerClient;

    @Before
    public void setUp() throws IOException {
        registry = DefaultGridRegistry.newInstance();
        
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
    public void testAuthenticationSucceedsForNoVnc() throws Exception {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/localhost/port/50000/?nginx=localhost:50000&view_only=true");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(200));
    }
    
    @Test
    public void testAuthenticationSucceedsForWebsockify() throws Exception {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/proxy/localhost:50000/websockify");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(200));
    }
    
    @Test
    public void testAuthenticationFailsForWebsockify() throws Exception {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/proxy/localhost:50002/websockify");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }
    
    @Test
    public void testAuthenticationFailsForVncWithBadPort() throws Exception {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/localhost/port/50003/?nginx=localhost:50003&view_only=true");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }
    
    @Test
    public void testAuthenticationFailsForVncWithBadHost() throws Exception {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/fakehost/port/50000/?nginx=fakehost:50000&view_only=true");
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }

    @Test
    public void testAuthenticationFailsWithNoHeader() throws Exception {
        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
        
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        
        vncAuthenticationServlet.doGet(request, response);
        
        verify(response).setStatus(statusCaptor.capture());
        
        assertThat(statusCaptor.getValue(), equalTo(403));
    }
}
