package de.zalando.tip.zalenium.servlet;


import de.zalando.tip.zalenium.proxy.DockerSeleniumRemoteProxy;
import de.zalando.tip.zalenium.proxy.DockerSeleniumStarterRemoteProxy;
import de.zalando.tip.zalenium.util.TestUtils;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;

public class LiveNodeServletTest {

    @Test
    public void addedNodesAreRenderedInServlet() throws ServletException, IOException {
        Registry registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest registrationRequest = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getCapabilities().clear();
        registrationRequest.getCapabilities().addAll(DockerSeleniumStarterRemoteProxy.getDockerSeleniumFallbackCapabilities());
        DockerSeleniumRemoteProxy proxyOne = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);
        registrationRequest = TestUtils.getRegistrationRequestForTesting(40001,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        registrationRequest.getCapabilities().clear();
        registrationRequest.getCapabilities().addAll(DockerSeleniumStarterRemoteProxy.getDockerSeleniumFallbackCapabilities());
        DockerSeleniumRemoteProxy proxyTwo = DockerSeleniumRemoteProxy.getNewInstance(registrationRequest, registry);

        registry.add(proxyOne);
        registry.add(proxyTwo);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

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

}
