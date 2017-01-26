package de.zalando.tip.zalenium.servlet;


import de.zalando.tip.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.tip.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import de.zalando.tip.zalenium.util.TestUtils;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.CoreMatchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

public class LiveNodeServletTest {

    private Registry registry;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @Before
    public void setUp() throws IOException {
        registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest registrationRequest = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getCapabilities().clear();
        registrationRequest.getCapabilities().addAll(DockerSeleniumStarterRemoteProxy.getCapabilities());
        DockerSeleniumRemoteProxy proxyOne = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);
        registrationRequest = TestUtils.getRegistrationRequestForTesting(40001,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getCapabilities().clear();
        List<DesiredCapabilities> capabilities = DockerSeleniumStarterRemoteProxy.getCapabilities();
        DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
        desiredCapabilities.setBrowserName("NEW_BROWSER");
        desiredCapabilities.setPlatform(Platform.LINUX);
        desiredCapabilities.setCapability(RegistrationRequest.MAX_INSTANCES, 1);
        capabilities.add(desiredCapabilities);
        registrationRequest.getCapabilities().addAll(capabilities);
        DockerSeleniumRemoteProxy proxyTwo = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);

        registry.add(proxyOne);
        registry.add(proxyTwo);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        when(request.getParameter("refresh")).thenReturn("1");
        when(request.getServerName()).thenReturn("localhost");
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            private StringBuilder stringBuilder = new StringBuilder();

            @Override
            public boolean isReady() {
                System.out.println("isReady");
                return false;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                System.out.println("setWriteListener");
            }

            @Override
            public void write(int b) throws IOException {
                this.stringBuilder.append((char) b );
            }

            public String toString() {
                return stringBuilder.toString();
            }
        });
    }

    @Test
    public void addedNodesAreRenderedInServlet() throws ServletException, IOException {

        live liveServlet = new live(registry);

        liveServlet.doPost(request, response);

        String responseContent = response.getOutputStream().toString();
        assertThat(responseContent, containsString("Zalenium Live Preview"));
        assertThat(responseContent, containsString("http://localhost:40000"));
        assertThat(responseContent, containsString("http://localhost:40001"));
        assertThat(responseContent, containsString("http://localhost:5555/proxy/50000/?nginx=50000&view_only=true'"));
        assertThat(responseContent, containsString("http://localhost:5555/proxy/50000/?nginx=50000&view_only=false'"));
        assertThat(responseContent, containsString("http://localhost:5555/proxy/50001/?nginx=50001&view_only=true'"));
        assertThat(responseContent, containsString("http://localhost:5555/proxy/50001/?nginx=50001&view_only=false'"));
    }

    @Test
    public void postAndGetReturnSameContent() throws ServletException, IOException {

        live liveServlet = new live(registry);

        liveServlet.doPost(request, response);
        String postResponseContent = response.getOutputStream().toString();

        liveServlet.doGet(request, response);
        String getResponseContent = response.getOutputStream().toString();
        assertThat(getResponseContent, containsString(postResponseContent));
    }

    @Test
    public void noRefreshInHtmlWhenParameterIsInvalid() throws ServletException, IOException {
        when(request.getParameter("refresh")).thenReturn("XYZ");

        live liveServlet = new live(registry);

        liveServlet.doPost(request, response);
        String postResponseContent = response.getOutputStream().toString();
        assertThat(postResponseContent, not(containsString("<meta http-equiv='refresh'")));
    }

}
