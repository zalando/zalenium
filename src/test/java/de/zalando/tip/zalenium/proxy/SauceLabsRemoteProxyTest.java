package de.zalando.tip.zalenium.proxy;

import com.google.gson.JsonElement;
import de.zalando.tip.zalenium.util.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.ExternalSessionKey;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

public class SauceLabsRemoteProxyTest {

    private static SauceLabsRemoteProxy sauceLabsProxy;
    private static Registry registry;


    @BeforeClass
    public static void setUp() {
        registry = Registry.newInstance();
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30001,
                SauceLabsRemoteProxy.class.getCanonicalName());
        sauceLabsProxy = SauceLabsRemoteProxy.getNewInstance(request, registry);

        // we need to register a DockerSeleniumStarter proxy to have a proper functioning SauceLabsProxy
        request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
        DockerSeleniumStarterRemoteProxy dsStarterProxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // We add both nodes to the registry
        registry.add(sauceLabsProxy);
        registry.add(dsStarterProxy);
    }

    @Test
    public void doesNotCreateSessionWhenDockerSeleniumCanProcessRequest() {
        // This capability is supported by docker-selenium, so it should return a null session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);

        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);

        Assert.assertNull(testSession);
    }

    @Test
    public void missingBrowserCapabilityDoesNotCreateSession() {
        // Non existent capability that should not create a session
        Map<String, Object> nonSupportedCapability = new HashMap<>();
        nonSupportedCapability.put(CapabilityType.PLATFORM, Platform.EL_CAPITAN);

        TestSession testSession = sauceLabsProxy.getNewSession(nonSupportedCapability);

        Assert.assertNull(testSession);
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.EDGE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN10);

        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before SauceLabsProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(SauceLabsRemoteProxy.class, sorted.get(1).getClass());
    }

    @Test
    public void checkBeforeSessionInvocation() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.MAC);

        // Getting a test session in the sauce labs node
        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the SauceLabs user and api key get added to the body request.
        WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession();

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.forward(request, response, true);

        Environment env = new Environment();

        // The body should now have the SauceLabs variables
        String expectedBody = String.format("{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platform\":" +
                        "\"WIN8\",\"username\":\"%s\",\"accessKey\":\"%s\",\"version\":\"latest\"}}",
                env.getStringEnvVariable("SAUCE_USERNAME", ""),
                env.getStringEnvVariable("SAUCE_ACCESS_KEY", ""));
        verify(request).setBody(expectedBody);
    }

    @Test
    public void testInformationIsRetrievedWhenStoppingSession() throws IOException {
        try {
            // Capability which should result in a created session
            Map<String, Object> requestedCapability = new HashMap<>();
            requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
            requestedCapability.put(CapabilityType.PLATFORM, Platform.MAC);

            // Getting a test session in the sauce labs node
            SauceLabsRemoteProxy sauceLabsSpyProxy = spy(sauceLabsProxy);
            JsonElement informationSample = TestUtils.getTestInformationSample("saucelabs_testinformation.json");
            CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
            Environment env = new Environment();
            String mockTestInformationUrl = "https://%s:%s@saucelabs.com/rest/v1/%s/jobs/72e4f8ecf04440fe965faf657864ed52";
            mockTestInformationUrl = String.format(mockTestInformationUrl, env.getStringEnvVariable("SAUCE_USERNAME", ""),
                    env.getStringEnvVariable("SAUCE_ACCESS_KEY", ""),
                    env.getStringEnvVariable("SAUCE_USERNAME", ""));
            when(commonProxyUtilities.readJSONFromUrl(mockTestInformationUrl)).thenReturn(informationSample);
            when(commonProxyUtilities.readJSONFromFile(anyString())).thenCallRealMethod();
            SauceLabsRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);

            TestSession testSession = sauceLabsSpyProxy.getNewSession(requestedCapability);
            Assert.assertNotNull(testSession);
            String mockSeleniumSessionId = "72e4f8ecf04440fe965faf657864ed52";
            testSession.setExternalKey(new ExternalSessionKey(mockSeleniumSessionId));

            // We release the session, the node should be free
            WebDriverRequest request = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(request.getMethod()).thenReturn("DELETE");
            when(request.getRequestType()).thenReturn(RequestType.STOP_SESSION);
            testSession.getSlot().doFinishRelease();
            sauceLabsSpyProxy.afterCommand(testSession, request, response);

            verify(sauceLabsSpyProxy, timeout(1000 * 5)).getTestInformation(mockSeleniumSessionId);
            TestInformation testInformation = sauceLabsSpyProxy.getTestInformation(mockSeleniumSessionId);
            Assert.assertEquals(mockSeleniumSessionId, testInformation.getTestName());
            Assert.assertThat(testInformation.getFileName(),
                    CoreMatchers.containsString("saucelabs_72e4f8ecf04440fe965faf657864ed52_googlechrome_Windows_2008"));
            Assert.assertEquals("googlechrome 56, Windows 2008", testInformation.getBrowserAndPlatform());
            Assert.assertThat(testInformation.getVideoUrl(),
                    CoreMatchers.containsString("jobs/72e4f8ecf04440fe965faf657864ed52/assets/video.flv"));
        } finally {
            SauceLabsRemoteProxy.restoreCommonProxyUtilities();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void useSauceLabsFallbackCapabilitiesFileWhenTheOnesFromSauceLabsAreNotAvailable() {
        try {
            // Mocking the utility class that fetches the json from a given url
            URL resource = this.getClass().getClassLoader().getResource("saucelabs_capabilities.json");
            File fileLocation = new File(resource.getPath());
            CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
            when(commonProxyUtilities.readJSONFromUrl(anyString())).thenReturn(null);
            when(commonProxyUtilities.readJSONFromFile(anyString())).thenCallRealMethod();
            when(commonProxyUtilities.currentLocalPath()).thenReturn(fileLocation.getParent());
            SauceLabsRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);

            RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30001,
                    SauceLabsRemoteProxy.class.getCanonicalName());

            // Assuring the capabilities are empty
            Assert.assertTrue(request.getCapabilities().isEmpty());

            request = SauceLabsRemoteProxy.updateSLCapabilities(request,
                    SauceLabsRemoteProxy.SAUCE_LABS_CAPABILITIES_URL);

            // Now the capabilities should be filled even if the url was not fetched
            Assert.assertFalse(request.getCapabilities().isEmpty());
        } finally {
            SauceLabsRemoteProxy.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void testEventIsInvoked() throws IOException {
        try {
            // Capability which should result in a created session
            Map<String, Object> requestedCapability = new HashMap<>();
            requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
            requestedCapability.put(CapabilityType.PLATFORM, Platform.MAC);

            // Getting a test session in the sauce labs node
            TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);
            Assert.assertNotNull(testSession);

            // We release the sessions and invoke the afterCommand with a mocked object
            Environment env = mock(Environment.class);
            when(env.getBooleanEnvVariable("ZALENIUM_SEND_ANONYMOUS_USAGE_INFO", false))
                    .thenReturn(true);
            when(env.getStringEnvVariable("ZALENIUM_GA_API_VERSION", "")).thenReturn("1");
            when(env.getStringEnvVariable("ZALENIUM_GA_TRACKING_ID", "")).thenReturn("UA-88441352");
            when(env.getStringEnvVariable("ZALENIUM_GA_ENDPOINT", ""))
                    .thenReturn("https://www.google-analytics.com/collect");
            when(env.getStringEnvVariable("ZALENIUM_GA_ANONYMOUS_CLIENT_ID", ""))
                    .thenReturn("RANDOM_STRING");

            HttpClient client = mock(HttpClient.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            when(client.execute(any(HttpPost.class))).thenReturn(httpResponse);


            GoogleAnalyticsApi ga = new GoogleAnalyticsApi();
            GoogleAnalyticsApi gaSpy = spy(ga);
            gaSpy.setEnv(env);
            gaSpy.setHttpClient(client);
            SauceLabsRemoteProxy.setGa(gaSpy);

            WebDriverRequest webDriverRequest = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("DELETE");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.STOP_SESSION);

            testSession.getSlot().doFinishRelease();
            testSession.setExternalKey(new ExternalSessionKey("testKey"));
            sauceLabsProxy.afterCommand(testSession, webDriverRequest, response);

            verify(gaSpy, times(1)).testEvent(anyString(), anyString(), anyLong());
        } finally {
            SauceLabsRemoteProxy.restoreGa();
        }

    }


}
