package de.zalando.tip.zalenium.proxy;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.GridRole;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class DockerSeleniumStarterRemoteProxyTest {

    private DockerSeleniumStarterRemoteProxy spyProxy;

    @Before
    public void setup() throws DockerException, InterruptedException {
        Registry registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = new RegistrationRequest();
        request.setRole(GridRole.NODE);
        request.getConfiguration().put(RegistrationRequest.MAX_SESSION, 5);
        request.getConfiguration().put(RegistrationRequest.AUTO_REGISTER, true);
        request.getConfiguration().put(RegistrationRequest.REGISTER_CYCLE, 5000);
        request.getConfiguration().put(RegistrationRequest.HUB_HOST, "localhost");
        request.getConfiguration().put(RegistrationRequest.HUB_PORT, 4444);
        request.getConfiguration().put(RegistrationRequest.PORT, 30000);
        request.getConfiguration().put(RegistrationRequest.PROXY_CLASS, "de.zalando.tip.zalenium.proxy.DockerSeleniumStarterRemoteProxy");
        request.getConfiguration().put(RegistrationRequest.REMOTE_HOST, "http://localhost:4444");

        // Creating the proxy
        DockerSeleniumStarterRemoteProxy proxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // Mock the docker client
        DockerClient dockerClient = mock(DockerClient.class);
        ContainerCreation containerCreation = new ContainerCreation("ANY_STRING");
        when(dockerClient.createContainer((ContainerConfig)notNull())).thenReturn(containerCreation);

        DockerSeleniumStarterRemoteProxy.setDockerClient(dockerClient);

        // Spying on the proxy to see if methods are invoked or not
        spyProxy = spy(proxy);

    }

    @AfterClass
    public static void tearDown() {
        DockerSeleniumStarterRemoteProxy.restoreDockerClient();
    }

    @Test
    public void noContainerIsStartedWhenCapabilitiesAreNotSupported() throws DockerException, InterruptedException {

        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<String, Object>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.MAC);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(anyString());
    }

    @Test
    public void noContainerIsStartedWhenPlatformIsNotSupported() {
        // Non supported desired capability for the test session
        Map<String, Object> nonSupportedCapability = new HashMap<String, Object>();
        nonSupportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.WINDOWS);
        TestSession testSession = spyProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, never()).startDockerSeleniumContainer(anyString());
    }

    @Test
    public void containerIsStartedWhenChromeCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<String, Object>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, times(1)).startDockerSeleniumContainer(BrowserType.CHROME);
    }

    @Test
    public void containerIsStartedWhenFirefoxCapabilitiesAreSupported() {

        // Supported desired capability for the test session
        Map<String, Object> supportedCapability = new HashMap<String, Object>();
        supportedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.FIREFOX);
        supportedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        TestSession testSession = spyProxy.getNewSession(supportedCapability);

        Assert.assertNull(testSession);
        verify(spyProxy, times(1)).startDockerSeleniumContainer(BrowserType.FIREFOX);
    }

    /*
        The following tests check that if for any reason the capabilities from DockerSelenium cannot be
        fetched, it should fallback to the default ones.
     */

    @Test
    public void fallBackToDefaultCapabilitiesWhenWebCapabilitiesUrlIsNotValid() {
        RegistrationRequest registrationRequest = new RegistrationRequest();
        String url = "this_is_not_a_url";
        registrationRequest = DockerSeleniumStarterRemoteProxy.updateDSCapabilities(registrationRequest, url);
        Assert.assertEquals("Should return 2 capabilities", registrationRequest.getCapabilities().size(), 2);
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.FIREFOX));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.CHROME));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(Platform.LINUX.name()));
    }

    @Test
    public void fallBackToDefaultCapabilitiesWhenWebCapabilitiesUrlReturnsNoJson() {
        RegistrationRequest registrationRequest = new RegistrationRequest();
        String url = "https://www.google.com";
        registrationRequest = DockerSeleniumStarterRemoteProxy.updateDSCapabilities(registrationRequest, url);
        Assert.assertEquals("Should return 2 capabilities", registrationRequest.getCapabilities().size(), 2);
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.FIREFOX));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.CHROME));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(Platform.LINUX.name()));
    }

    @Test
    public void fallBackToDefaultCapabilitiesWhenWebCapabilitiesUrlReturnsAJsonWithWrongFields() {
        RegistrationRequest registrationRequest = new RegistrationRequest();
        String url = "http://ip.jsontest.com/";
        registrationRequest = DockerSeleniumStarterRemoteProxy.updateDSCapabilities(registrationRequest, url);
        Assert.assertEquals("Should return 2 capabilities", registrationRequest.getCapabilities().size(), 2);
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.FIREFOX));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(BrowserType.CHROME));
        Assert.assertThat(registrationRequest.getCapabilities().toString(), CoreMatchers.containsString(Platform.LINUX.name()));
    }
}
