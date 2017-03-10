package de.zalando.tip.zalenium.proxy;


import com.google.gson.JsonObject;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import de.zalando.tip.zalenium.util.Environment;
import de.zalando.tip.zalenium.util.TestUtils;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import static org.mockito.Mockito.*;

public class TestingBotRemoteProxyTest {

    private TestingBotRemoteProxy testingBotProxy;
    private Registry registry;

    @SuppressWarnings("ConstantConditions")
    @Before
    public void setUp() {
        registry = Registry.newInstance();
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30002,
                TestingBotRemoteProxy.class.getCanonicalName());
        URL resource = TestingBotRemoteProxyTest.class.getClassLoader().getResource("testingbot_capabilities.json");
        File fileLocation = new File(resource.getPath());
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString())).thenReturn(null);
        when(commonProxyUtilities.readJSONFromFile(anyString())).thenCallRealMethod();
        when(commonProxyUtilities.currentLocalPath()).thenReturn(fileLocation.getParent());
        TestingBotRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        testingBotProxy = TestingBotRemoteProxy.getNewInstance(request, registry);

        // we need to register a DockerSeleniumStarter proxy to have a proper functioning testingBotProxy
        request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
        DockerSeleniumStarterRemoteProxy dsStarterProxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // We add both nodes to the registry
        registry.add(testingBotProxy);
        registry.add(dsStarterProxy);
    }

    @After
    public void tearDown() {
        TestingBotRemoteProxy.restoreCommonProxyUtilities();
        TestingBotRemoteProxy.restoreGa();
        TestingBotRemoteProxy.restoreEnvironment();
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before SauceLabsProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(TestingBotRemoteProxy.class, sorted.get(1).getClass());
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
    }

    @Test
    public void credentialsAreAddedInSessionCreation() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

        // Getting a test session in the sauce labs node
        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the SauceLabs user and api key get added to the body request.
        WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession();

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.forward(request, response, true);

        Environment env = new Environment();

        // The body should now have the TestingBot variables
        String expectedBody = String.format("{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platform\":" +
                                "\"WIN8\",\"key\":\"%s\",\"secret\":\"%s\"}}",
                        env.getStringEnvVariable("TESTINGBOT_KEY", ""),
                        env.getStringEnvVariable("TESTINGBOT_SECRET", ""));
        verify(request).setBody(expectedBody);
    }

    @Test
    public void requestIsNotModifiedInOtherRequestTypes() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

        // Getting a test session in the sauce labs node
        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the SauceLabs user and api key get added to the body request.
        WebDriverRequest request = mock(WebDriverRequest.class);
        when(request.getRequestURI()).thenReturn("session");
        when(request.getServletPath()).thenReturn("session");
        when(request.getContextPath()).thenReturn("");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestType()).thenReturn(RequestType.REGULAR);
        JsonObject jsonObject = new JsonObject();
        JsonObject desiredCapabilities = new JsonObject();
        desiredCapabilities.addProperty(CapabilityType.BROWSER_NAME, BrowserType.IE);
        desiredCapabilities.addProperty(CapabilityType.PLATFORM, Platform.WIN8.name());
        jsonObject.add("desiredCapabilities", desiredCapabilities);
        when(request.getBody()).thenReturn(jsonObject.toString());

        Enumeration<String> strings = Collections.emptyEnumeration();
        when(request.getHeaderNames()).thenReturn(strings);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.forward(request, response, true);

        // The body should not be affected and not contain the TestingBot variables
        Assert.assertThat(request.getBody(), CoreMatchers.containsString(jsonObject.toString()));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("key")));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("secret")));

        when(request.getMethod()).thenReturn("GET");

        testSession.forward(request, response, true);
        Assert.assertThat(request.getBody(), CoreMatchers.containsString(jsonObject.toString()));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("key")));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("secret")));
    }

    @Test
    public void checkVideoFileExtensionAndProxyNameAndVideoUrl() {
        Assert.assertEquals(".mp4", testingBotProxy.getVideoFileExtension());
        Assert.assertEquals("TestingBot", testingBotProxy.getProxyName());
    }

}
