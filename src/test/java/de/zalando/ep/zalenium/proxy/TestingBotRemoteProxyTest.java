package de.zalando.ep.zalenium.proxy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

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

import de.zalando.ep.zalenium.dashboard.DashboardCollection;
import de.zalando.ep.zalenium.dashboard.Dashboard;
import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

public class TestingBotRemoteProxyTest {

    private TestingBotRemoteProxy testingBotProxy;
    private GridRegistry registry;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @SuppressWarnings("ConstantConditions")
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
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30002,
                TestingBotRemoteProxy.class.getCanonicalName());

        JsonElement informationSample = TestUtils.getTestInformationSample("testingbot_testinformation.json");

        String userInfoUrl = "https://api.testingbot.com/v1/user";
        Environment env = new Environment();
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(userInfoUrl,
                env.getStringEnvVariable("TESTINGBOT_KEY", ""),
                env.getStringEnvVariable("TESTINGBOT_SECRET", ""))).thenReturn(null);

        String mockTestInfoUrl = "https://api.testingbot.com/v1/tests/2cf5d115-ca6f-4bc4-bc06-a4fca00836ce";
        when(commonProxyUtilities.readJSONFromUrl(mockTestInfoUrl,
                env.getStringEnvVariable("TESTINGBOT_KEY", ""),
                env.getStringEnvVariable("TESTINGBOT_SECRET", ""))).thenReturn(informationSample);
        TestingBotRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        testingBotProxy = TestingBotRemoteProxy.getNewInstance(request, registry);

        // Temporal folder for dashboard files
        TestUtils.ensureRequiredInputFilesExist(temporaryFolder);

        // We add both nodes to the registry
        registry.add(testingBotProxy);
        
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest proxyRequest = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        proxyRequest.getConfiguration().capabilities.clear();
        proxyRequest.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());

        // Creating the proxy
        RemoteProxy remoteProxy = DockerSeleniumRemoteProxy.getNewInstance(proxyRequest, registry);
        registry.add(remoteProxy);
    }

    @After
    public void tearDown() throws MalformedObjectNameException {
        ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://hub.testingbot.com:80\"");
        new JMXHelper().unregister(objectName);
        objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
        new JMXHelper().unregister(objectName);
        TestingBotRemoteProxy.restoreCommonProxyUtilities();
        TestingBotRemoteProxy.restoreGa();
        TestingBotRemoteProxy.restoreEnvironment();
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before TestingBotProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(TestingBotRemoteProxy.class, sorted.get(1).getClass());
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN8);

        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
    }

    @Test
    public void credentialsAreAddedInSessionCreation() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN8);

        // Getting a test session in the TestingBot node
        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the TestingBot user and api key get added to the body request.
        WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession(BrowserType.IE, Platform.WIN8);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.forward(request, response, true);

        Environment env = new Environment();

        // The body should now have the TestingBot variables
        String expectedBody = String.format("{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platformName\":" +
                                "\"WIN8\",\"key\":\"%s\",\"secret\":\"%s\",\"version\":\"latest\"}}",
                        env.getStringEnvVariable("TESTINGBOT_KEY", ""),
                        env.getStringEnvVariable("TESTINGBOT_SECRET", ""));
        verify(request).setBody(expectedBody);
    }

    @Test
    public void requestIsNotModifiedInOtherRequestTypes() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN8);

        // Getting a test session in the TestingBot node
        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the TestingBot user and api key get added to the body request.
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
    public void testInformationIsRetrievedWhenStoppingSession() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

        // Getting a test session in the TestingBot node
        TestingBotRemoteProxy spyProxy = spy(testingBotProxy);
        TestSession testSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);
        String mockSeleniumSessionId = "2cf5d115-ca6f-4bc4-bc06-a4fca00836ce";
        testSession.setExternalKey(new ExternalSessionKey(mockSeleniumSessionId));

        // We release the session, the node should be free
        WebDriverRequest request = mock(WebDriverRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestType()).thenReturn(RequestType.STOP_SESSION);

        testSession.getSlot().doFinishRelease();
        spyProxy.afterCommand(testSession, request, response);

        verify(spyProxy, timeout(1000 * 5)).getTestInformation(mockSeleniumSessionId);
        TestInformation testInformation = spyProxy.getTestInformation(mockSeleniumSessionId);
        Assert.assertEquals("loadZalandoPageAndCheckTitle", testInformation.getTestName());
        Assert.assertThat(testInformation.getFileName(), CoreMatchers.containsString("testingbot_loadZalandoPageAndCheckTitle_Safari9_CAPITAN"));
        Assert.assertEquals("Safari9 9, CAPITAN", testInformation.getBrowserAndPlatform());
        Assert.assertEquals("https://s3-eu-west-1.amazonaws.com/eurectestingbot/2cf5d115-ca6f-4bc4-bc06-a4fca00836ce.mp4",
                testInformation.getVideoUrl());
    }

    @Test
    public void dashboardFilesGetCopied() {
        try {
            // Capability which should result in a created session
            Map<String, Object> requestedCapability = new HashMap<>();
            requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
            requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

            // Getting a test session in the TestingBot node
            TestingBotRemoteProxy spyProxy = spy(testingBotProxy);
            TestSession testSession = spyProxy.getNewSession(requestedCapability);
            Assert.assertNotNull(testSession);
            String mockSeleniumSessionId = "2cf5d115-ca6f-4bc4-bc06-a4fca00836ce";
            testSession.setExternalKey(new ExternalSessionKey(mockSeleniumSessionId));

            // We release the session, the node should be free
            WebDriverRequest request = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(request.getMethod()).thenReturn("DELETE");
            when(request.getRequestType()).thenReturn(RequestType.STOP_SESSION);

            testSession.getSlot().doFinishRelease();
            spyProxy.afterCommand(testSession, request, response);

            CommonProxyUtilities proxyUtilities = TestUtils.mockCommonProxyUtilitiesForDashboardTesting(temporaryFolder);
            Dashboard.setCommonProxyUtilities(proxyUtilities);

            TestInformation testInformation = spyProxy.getTestInformation(mockSeleniumSessionId);
            DashboardCollection.updateDashboard(testInformation);
            File videosFolder = new File(temporaryFolder.getRoot().getAbsolutePath(), "videos");
            Assert.assertTrue(videosFolder.isDirectory());
            File amountOfRunTests = new File(videosFolder, "executedTestsInfo.json");
            Assert.assertTrue(amountOfRunTests.exists());
            File dashboard = new File(videosFolder, "dashboard.html");
            Assert.assertTrue(dashboard.exists());
            Assert.assertTrue(dashboard.isFile());
            File testList = new File(videosFolder, "list.html");
            Assert.assertTrue(testList.exists());
            Assert.assertTrue(testList.isFile());
            File cssFolder = new File(videosFolder, "css");
            Assert.assertTrue(cssFolder.exists());
            Assert.assertTrue(cssFolder.isDirectory());
            File jsFolder = new File(videosFolder, "js");
            Assert.assertTrue(jsFolder.exists());
            Assert.assertTrue(jsFolder.isDirectory());
        } finally {
            Dashboard.restoreCommonProxyUtilities();
        }
    }

    @Test
    public void checkVideoFileExtensionAndProxyName() {
        Assert.assertEquals(".mp4", testingBotProxy.getVideoFileExtension());
        Assert.assertEquals("TestingBot", testingBotProxy.getProxyName());
    }

}
