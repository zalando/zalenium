package de.zalando.tip.zalenium.proxy;


import com.google.gson.JsonObject;
import de.zalando.tip.zalenium.util.CommonProxyUtilities;
import de.zalando.tip.zalenium.util.Environment;
import de.zalando.tip.zalenium.util.GoogleAnalyticsApi;
import de.zalando.tip.zalenium.util.TestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.BeforeClass;
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

public class BrowserStackRemoteProxyTest {

    private static BrowserStackRemoteProxy browserStackProxy;
    private static Registry registry;

    @SuppressWarnings("ConstantConditions")
    @BeforeClass
    public static void setUp() {
        registry = Registry.newInstance();
        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30002,
                BrowserStackRemoteProxy.class.getCanonicalName());
        URL resource = BrowserStackRemoteProxyTest.class.getClassLoader().getResource("browserstack_capabilities.json");
        File fileLocation = new File(resource.getPath());
        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString())).thenReturn(null);
        when(commonProxyUtilities.readJSONFromFile(anyString())).thenCallRealMethod();
        when(commonProxyUtilities.currentLocalPath()).thenReturn(fileLocation.getParent());
        BrowserStackRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
        browserStackProxy = BrowserStackRemoteProxy.getNewInstance(request, registry);

        // we need to register a DockerSeleniumStarter proxy to have a proper functioning BrowserStackProxy
        request = TestUtils.getRegistrationRequestForTesting(30000,
                DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
        DockerSeleniumStarterRemoteProxy dsStarterProxy = DockerSeleniumStarterRemoteProxy.getNewInstance(request, registry);

        // We add both nodes to the registry
        registry.add(browserStackProxy);
        registry.add(dsStarterProxy);
    }

    @Test
    public void checkProxyOrdering() {
        // Checking that the DockerSeleniumStarterProxy should come before SauceLabsProxy
        List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
        Assert.assertEquals(2, sorted.size());
        Assert.assertEquals(DockerSeleniumStarterRemoteProxy.class, sorted.get(0).getClass());
        Assert.assertEquals(BrowserStackRemoteProxy.class, sorted.get(1).getClass());
    }

    @Test
    public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

        TestSession testSession = browserStackProxy.getNewSession(requestedCapability);

        Assert.assertNotNull(testSession);
    }

    @Test
    public void credentialsAreAddedInSessionCreation() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

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
        when(request.getRequestType()).thenReturn(RequestType.START_SESSION);
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

        // The body should now have the BrowserStack variables
        String expectedBody = System.getenv("BROWSER_STACK_USER") == null ?
                String.format("{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platform\":" +
                                "\"WIN8\",\"browserstack.user\":%s,\"browserstack.key\":%s}}", System.getenv("BROWSER_STACK_USER"),
                        System.getenv("BROWSER_STACK_KEY")) :
                String.format("{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platform\":" +
                                "\"WIN8\",\"browserstack.user\":\"%s\",\"browserstack.key\":\"%s\"}}", System.getenv("BROWSER_STACK_USER"),
                        System.getenv("BROWSER_STACK_KEY"));
        verify(request).setBody(expectedBody);
    }

    @Test
    public void requestIsNotModifiedInOtherRequestTypes() throws IOException {
        // Capability which should result in a created session
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN8);

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
        desiredCapabilities.addProperty(CapabilityType.PLATFORM, Platform.WIN8.name());
        jsonObject.add("desiredCapabilities", desiredCapabilities);
        when(request.getBody()).thenReturn(jsonObject.toString());

        Enumeration<String> strings = Collections.emptyEnumeration();
        when(request.getHeaderNames()).thenReturn(strings);

        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream stream = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);

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
    public void gaGetsInvokedWhenExceptionsAreCaught() throws IOException {
        // Mocking environment and Google Analytics class.
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
        BrowserStackRemoteProxy.setGa(gaSpy);

        CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
        when(commonProxyUtilities.readJSONFromUrl(anyString())).thenCallRealMethod();
        when(commonProxyUtilities.readJSONFromFile(anyString())).thenReturn(null);
        BrowserStackRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);


        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30002,
                BrowserStackRemoteProxy.class.getCanonicalName());
        browserStackProxy = BrowserStackRemoteProxy.getNewInstance(request, registry);
        
        verify(gaSpy, times(1)).trackException(any(Exception.class));
    }

}
