package de.zalando.ep.zalenium.proxy;


import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.awaitility.Duration;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
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
import com.google.gson.JsonObject;

import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

public class BrowserStackRemoteProxyTest {

    private BrowserStackRemoteProxy browserStackProxy;
    private GridRegistry registry;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @SuppressWarnings("ConstantConditions")
    @Before
    public void setUp() {
        try {
            ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
            ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
            new JMXHelper().unregister(objectName);
        } catch (MalformedObjectNameException | InstanceNotFoundException e) {
            // Might be that the object does not exist, it is ok. Nothing to do, this is just a cleanup task.
        }
        registry = new SimpleRegistry();
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30002,
                BrowserStackRemoteProxy.class.getCanonicalName());
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString(), anyString(), anyString())).thenReturn(null);
        BrowserStackRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        browserStackProxy = BrowserStackRemoteProxy.getNewInstance(request, registry);

        // We add both nodes to the registry
        registry.add(browserStackProxy);
        
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
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://hub-cloud.browserstack.com:80\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
        new JMXHelper().unregister(objectName);
        BrowserStackRemoteProxy.restoreCommonProxyUtilities();
        BrowserStackRemoteProxy.restoreGa();
        BrowserStackRemoteProxy.restoreEnvironment();
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before BrowserStackRemoteProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(BrowserStackRemoteProxy.class, sorted.get(1).getClass());
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

        Assert.assertEquals(0, browserStackProxy.getNumberOfSessions());
        TestSession testSession = browserStackProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
        Assert.assertEquals(1, browserStackProxy.getNumberOfSessions());
    }



    @Test
    public void credentialsAreAddedInSessionCreation() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN8);

        // Getting a test session in the sauce labs node
        TestSession testSession = browserStackProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the BrowserStack user and api key get added to the body request.
        WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession(BrowserType.IE, Platform.WIN8);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.setExternalKey(new ExternalSessionKey("BrowserStack Test"));
        testSession.forward(request, response, true);

        Environment env = new Environment();

        // The body should now have the BrowserStack variables
        String expectedBody = String.format("{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platformName\":" +
                                "\"WIN8\",\"browserstack.user\":\"%s\",\"browserstack.key\":\"%s\"}}",
                        env.getStringEnvVariable("BROWSER_STACK_USER", ""),
                        env.getStringEnvVariable("BROWSER_STACK_KEY", ""));
        verify(request).setBody(expectedBody);
    }

    @Test
    public void testInformationIsRetrievedWhenStoppingSession() throws IOException {
        // Capability which should result in a created session
        try {
            Map<String, Object> requestedCapability = new HashMap<>();
            requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
            requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN10);

            JsonElement informationSample = TestUtils.getTestInformationSample("browserstack_testinformation.json");
            TestUtils.ensureRequiredInputFilesExist(temporaryFolder);
            CommonProxyUtilities commonProxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Environment env = new Environment();
            String mockTestInformationUrl = "https://www.browserstack.com/automate/sessions/77e51cead8e6e37b0a0feb0dfa69325b2c4acf97.json";
            when(commonProxyUtilities.readJSONFromUrl(mockTestInformationUrl,
                    env.getStringEnvVariable("BROWSER_STACK_USER", ""),
                    env.getStringEnvVariable("BROWSER_STACK_KEY", ""))).thenReturn(informationSample);
            BrowserStackRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
            Dashboard.setCommonProxyUtilities(commonProxyUtilities);

            // Getting a test session in the sauce labs node
            BrowserStackRemoteProxy bsSpyProxy = spy(browserStackProxy);
            TestSession testSession = bsSpyProxy.getNewSession(requestedCapability);
            Assert.assertNotNull(testSession);
            String mockSeleniumSessionId = "77e51cead8e6e37b0a0feb0dfa69325b2c4acf97";
            testSession.setExternalKey(new ExternalSessionKey(mockSeleniumSessionId));

            // We release the session, the node should be free
            WebDriverRequest request = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(request.getMethod()).thenReturn("DELETE");
            when(request.getRequestType()).thenReturn(RequestType.STOP_SESSION);
            testSession.getSlot().doFinishRelease();
            bsSpyProxy.afterCommand(testSession, request, response);

            verify(bsSpyProxy, timeout(1000 * 5)).getTestInformation(mockSeleniumSessionId);
            Callable<Boolean> callable = () -> BrowserStackRemoteProxy.addToDashboardCalled;
            await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(callable);
            TestInformation testInformation = bsSpyProxy.getTestInformation(mockSeleniumSessionId);
            Assert.assertEquals("loadZalandoPageAndCheckTitle", testInformation.getTestName());
            Assert.assertThat(testInformation.getFileName(),
                    CoreMatchers.containsString("browserstack_loadZalandoPageAndCheckTitle_safari_OS_X"));
            Assert.assertEquals("safari 6.2, OS X Mountain Lion", testInformation.getBrowserAndPlatform());
            Assert.assertEquals("https://www.browserstack.com/s3-upload/bs-video-logs-use/s3/77e51cead8e6e37b0" +
                            "a0feb0dfa69325b2c4acf97/video-77e51cead8e6e37b0a0feb0dfa69325b2c4acf97.mp4?AWSAccessKeyId=" +
                            "AKIAIOW7IEY5D4X2OFIA&Expires=1497088589&Signature=tQ9SCH1lgg6FjlBIhlTDwummLWc%3D&response-" +
                            "content-type=video%2Fmp4",
                    testInformation.getVideoUrl());

        } finally {
            BrowserStackRemoteProxy.restoreCommonProxyUtilities();
            BrowserStackRemoteProxy.restoreGa();
            BrowserStackRemoteProxy.restoreEnvironment();
            Dashboard.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void requestIsNotModifiedInOtherRequestTypes() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN8);

        // Getting a test session in the sauce labs node
        TestSession testSession = browserStackProxy.getNewSession(requestedCapability);
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
        desiredCapabilities.addProperty(CapabilityType.PLATFORM_NAME, Platform.WIN8.name());
        jsonObject.add("desiredCapabilities", desiredCapabilities);
        when(request.getBody()).thenReturn(jsonObject.toString());

        Enumeration<String> strings = Collections.emptyEnumeration();
        when(request.getHeaderNames()).thenReturn(strings);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.setExternalKey(new ExternalSessionKey("BrowserStack Test"));
        testSession.forward(request, response, true);

        // The body should not be affected and not contain the BrowserStack variables
        Assert.assertThat(request.getBody(), CoreMatchers.containsString(jsonObject.toString()));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("browserstack.user")));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("browserstack.key")));

        when(request.getMethod()).thenReturn("GET");

        testSession.forward(request, response, true);
        Assert.assertThat(request.getBody(), CoreMatchers.containsString(jsonObject.toString()));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("browserstack.user")));
        Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("browserstack.key")));
    }

    @Test
    public void checkVideoFileExtensionAndProxyName() {
        Assert.assertEquals(".mp4", browserStackProxy.getVideoFileExtension());
        Assert.assertEquals("BrowserStack", browserStackProxy.getProxyName());
    }
}
