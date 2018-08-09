package de.zalando.ep.zalenium.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.GridRegistry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.server.jmx.JMXHelper;

import com.google.gson.JsonElement;

import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.GoogleAnalyticsApi;
import de.zalando.ep.zalenium.util.TestUtils;

public class SauceLabsRemoteProxyTest {

    private SauceLabsRemoteProxy sauceLabsProxy;
    private GridRegistry registry;


    @Before
    public void setUp() {
        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
        }
        registry = new de.zalando.ep.zalenium.util.SimpleRegistry();
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30001,
                SauceLabsRemoteProxy.class.getCanonicalName());
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString(), anyString(), anyString())).thenReturn(null);
        SauceLabsRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        sauceLabsProxy = SauceLabsRemoteProxy.getNewInstance(request, registry);

        registry.add(sauceLabsProxy);
        
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest proxyRequest = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        proxyRequest.getConfiguration().capabilities.clear();
        proxyRequest.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());

        // Creating the proxy
        DockerSeleniumRemoteProxy proxy = DockerSeleniumRemoteProxy.getNewInstance(proxyRequest, registry);
        registry.add(proxy);
    }

    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"https://ondemand.saucelabs.com:443\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
        new JMXHelper().unregister(objectName);
        SauceLabsRemoteProxy.restoreCommonProxyUtilities();
        SauceLabsRemoteProxy.restoreGa();
        SauceLabsRemoteProxy.restoreEnvironment();
    }

    @Test
    public void doesNotCreateSessionWhenDockerSeleniumCanProcessRequest() {
        // This capability is supported by docker-selenium, so it should return a null session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.LINUX);

        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);

        Assert.assertNull(testSession);
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.EDGE);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN10);

        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before SauceLabsProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(SauceLabsRemoteProxy.class, sorted.get(1).getClass());
    }

    @Test
    public void checkBeforeSessionInvocation() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

        // Getting a test session in the sauce labs node
        TestSession testSession = sauceLabsProxy.getNewSession(requestedCapability);
        System.out.println(requestedCapability.toString());
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the SauceLabs user and api key get added to the body request.
        WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession(BrowserType.SAFARI, Platform.MAC);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.forward(request, response, true);

        Environment env = new Environment();

        // The body should now have the SauceLabs variables
        String expectedBody = String.format("{\"desiredCapabilities\":{\"browserName\":\"safari\",\"platformName\":" +
                        "\"MAC\",\"username\":\"%s\",\"accessKey\":\"%s\",\"version\":\"latest\"}}",
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
            requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

            // Getting a test session in the sauce labs node
            SauceLabsRemoteProxy sauceLabsSpyProxy = spy(sauceLabsProxy);
            JsonElement informationSample = TestUtils.getTestInformationSample("saucelabs_testinformation.json");
            CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
            when(commonProxyUtilities.readJSONFromUrl(anyString(), anyString(), anyString())).thenReturn(informationSample);
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
                    CoreMatchers.containsString("jobs/72e4f8ecf04440fe965faf657864ed52/assets/video.mp4"));
        } finally {
            SauceLabsRemoteProxy.restoreCommonProxyUtilities();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void nodeHasCapabilitiesEvenWhenUrlCallFails() {
        try {
            CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
            when(commonProxyUtilities.readJSONFromUrl(anyString(), anyString(), anyString())).thenReturn(null);
            SauceLabsRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);

            RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30001,
                    SauceLabsRemoteProxy.class.getCanonicalName());

            request = SauceLabsRemoteProxy.updateSLCapabilities(request, "");

            // Now the capabilities should be filled even if the url was not fetched
            Assert.assertFalse(request.getConfiguration().capabilities.isEmpty());
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
            requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

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

    @Test
    public void slotIsReleasedWhenTestIsIdle() throws IOException {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

        SauceLabsRemoteProxy sauceLabsSpyProxy = spy(sauceLabsProxy);

        // Set a short idle time
        sauceLabsSpyProxy.setMaxTestIdleTime(1L);

        // Start poller thread
        sauceLabsSpyProxy.startPolling();

        // Get a test session
        TestSession newSession = sauceLabsSpyProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);
        newSession.setExternalKey(new ExternalSessionKey("RANDOM_EXTERNAL_KEY"));

        // Start the session
        WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession(BrowserType.SAFARI, Platform.MAC);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);
        sauceLabsSpyProxy.beforeCommand(newSession, request, response);

        // The terminateIdleSessions() method should be called after a moment
        verify(sauceLabsSpyProxy, timeout(2000)).terminateIdleSessions();
        verify(sauceLabsSpyProxy, timeout(2000)).addTestToDashboard("RANDOM_EXTERNAL_KEY", false);
    }

}
