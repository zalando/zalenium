package de.zalando.ep.zalenium.proxy;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.zalando.ep.zalenium.util.*;
import org.apache.commons.io.FileUtils;
import org.hamcrest.CoreMatchers;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
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
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.*;

public class TestingBotRemoteProxyTest {

    private TestingBotRemoteProxy testingBotProxy;
    private Registry registry;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @SuppressWarnings("ConstantConditions")
    @Before
    public void setUp() throws IOException {
        registry = Registry.newInstance();
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
        when(commonProxyUtilities.readJSONFromFile(anyString())).thenCallRealMethod();
        TestingBotRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        testingBotProxy = TestingBotRemoteProxy.getNewInstance(request, registry);

        // we need to register a DockerSeleniumStarter proxy to have a proper functioning testingBotProxy
        request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
        DockerSeleniumStarterRemoteProxy dsStarterProxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // Temporal folder for dashboard files
        temporaryFolder.newFile("list_template.html");
        temporaryFolder.newFile("dashboard_template.html");
        temporaryFolder.newFile("zalando.ico");
        temporaryFolder.newFolder("css");
        temporaryFolder.newFolder("js");

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
        // Checking that the DockerSeleniumStarterProxy should come before TestingBotProxy
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

        // Getting a test session in the TestingBot node
        TestSession testSession = testingBotProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(testSession);

        // We need to mock all the needed objects to forward the session and see how in the beforeMethod
        // the TestingBot user and api key get added to the body request.
        WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession();

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

        testSession.forward(request, response, true);

        Environment env = new Environment();

        // The body should now have the TestingBot variables
        String expectedBody = String.format("{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platform\":" +
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
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

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
    public void testInformationIsRetrievedWhenStoppingSession() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.MAC);

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
    public void checkVideoFileExtensionAndProxyName() {
        Assert.assertEquals(".mp4", testingBotProxy.getVideoFileExtension());
        Assert.assertEquals("TestingBot", testingBotProxy.getProxyName());
    }

}
