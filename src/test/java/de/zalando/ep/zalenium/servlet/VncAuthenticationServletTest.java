package de.zalando.ep.zalenium.servlet;

public class VncAuthenticationServletTest {
//    private GridRegistry registry;
//    private HttpServletRequest request;
//    private HttpServletResponse response;
//    private Supplier<ContainerClient> originalContainerClient;
//
//    @Before
//    public void setUp() throws IOException {
//        try {
//            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
//            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
//            new JMXHelper().unregister(objectName);
//        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
//            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
//        }
//        registry = ZaleniumRegistry.newInstance(new Hub(new GridHubConfiguration()));
//        
//        this.originalContainerClient = ContainerFactory.getContainerClientGenerator();
//        ContainerFactory.setContainerClientGenerator(DockerContainerMock::getMockedDockerContainerClient);
//
//        // Creating the configuration and the registration request of the proxy (node)
//        RegistrationRequest registrationRequest = TestUtils.getRegistrationRequestForTesting(40000,
//                DockerSeleniumRemoteProxy.class.getCanonicalName());
//        registrationRequest.getConfiguration().capabilities.clear();
//        registrationRequest.getConfiguration().capabilities.addAll(DockerSeleniumStarterRemoteProxy.getCapabilities());
//        DockerSeleniumRemoteProxy proxyOne = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);
//
//        registrationRequest = TestUtils.getRegistrationRequestForTesting(40001,
//                DockerSeleniumRemoteProxy.class.getCanonicalName());
//        registrationRequest.getConfiguration().capabilities.clear();
//        registrationRequest.getConfiguration().capabilities.addAll(DockerSeleniumStarterRemoteProxy.getCapabilities());
//        DockerSeleniumRemoteProxy proxyTwo = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);
//
//        registry.add(proxyOne);
//        registry.add(proxyTwo);
//
//        request = mock(HttpServletRequest.class);
//        response = mock(HttpServletResponse.class);
//
//        when(request.getParameter("refresh")).thenReturn("1");
//        when(request.getServerName()).thenReturn("localhost");
//        when(response.getOutputStream()).thenReturn(TestUtils.getMockedServletOutputStream());
//    }
//
//    @After
//    public void tearDown() throws MalformedObjectNameException {
//        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40001\"");
//        new JMXHelper().unregister(objectName);
//        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:40000\"");
//        new JMXHelper().unregister(objectName);
//        ContainerFactory.setContainerClientGenerator(originalContainerClient);
//    }
//
//    @Test
//    public void testAuthenticationSucceedsForNoVnc() {
//        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
//        
//        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
//        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/localhost/port/50000/?nginx=localhost:50000&view_only=true");
//        
//        vncAuthenticationServlet.doGet(request, response);
//        
//        verify(response).setStatus(statusCaptor.capture());
//        
//        assertThat(statusCaptor.getValue(), equalTo(200));
//    }
//    
//    @Test
//    public void testAuthenticationSucceedsForWebsockify() {
//        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
//        
//        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
//        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/proxy/localhost:50000/websockify");
//        
//        vncAuthenticationServlet.doGet(request, response);
//        
//        verify(response).setStatus(statusCaptor.capture());
//        
//        assertThat(statusCaptor.getValue(), equalTo(200));
//    }
//    
//    @Test
//    public void testAuthenticationFailsForWebsockify() {
//        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
//        
//        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
//        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/proxy/localhost:50002/websockify");
//        
//        vncAuthenticationServlet.doGet(request, response);
//        
//        verify(response).setStatus(statusCaptor.capture());
//        
//        assertThat(statusCaptor.getValue(), equalTo(403));
//    }
//    
//    @Test
//    public void testAuthenticationFailsForVncWithBadPort() {
//        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
//        
//        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
//        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/localhost/port/50003/?nginx=localhost:50003&view_only=true");
//        
//        vncAuthenticationServlet.doGet(request, response);
//        
//        verify(response).setStatus(statusCaptor.capture());
//        
//        assertThat(statusCaptor.getValue(), equalTo(403));
//    }
//    
//    @Test
//    public void testAuthenticationFailsForVncWithBadHost() {
//        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
//        
//        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
//        when(request.getHeader("X-Original-URI")).thenReturn("http://localhost/vnc/host/fakehost/port/50000/?nginx=fakehost:50000&view_only=true");
//        
//        vncAuthenticationServlet.doGet(request, response);
//        
//        verify(response).setStatus(statusCaptor.capture());
//        
//        assertThat(statusCaptor.getValue(), equalTo(403));
//    }
//
//    @Test
//    public void testAuthenticationFailsWithNoHeader() {
//        VncAuthenticationServlet vncAuthenticationServlet = new VncAuthenticationServlet(registry);
//        
//        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
//        
//        vncAuthenticationServlet.doGet(request, response);
//        
//        verify(response).setStatus(statusCaptor.capture());
//        
//        assertThat(statusCaptor.getValue(), equalTo(403));
//    }
}
