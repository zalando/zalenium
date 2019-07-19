package de.zalando.ep.zalenium.proxy;

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

import de.zalando.ep.zalenium.dashboard.TestInformation;
import de.zalando.ep.zalenium.util.CommonProxyUtilities;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.SimpleRegistry;
import de.zalando.ep.zalenium.util.TestUtils;

public class LambdaTestRemoteProxyTest {

  private LambdaTestRemoteProxy lambdaTestProxy;
  private GridRegistry registry;

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Before
  public void setUp() {
    try {
      ObjectName objectName = new ObjectName("org.seleniumhq.grid:type=Hub");
      ManagementFactory.getPlatformMBeanServer().getObjectInstance(objectName);
      new JMXHelper().unregister(objectName);
    } catch (MalformedObjectNameException | InstanceNotFoundException e) {
      // Might be that the object does not exist, it is ok. Nothing to do, this is
      // just a cleanup task.
    }
    registry = new SimpleRegistry();
    // Creating the configuration and the registration request of the proxy (node)
    RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30005,
        LambdaTestRemoteProxy.class.getCanonicalName());
    CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
    when(commonProxyUtilities.readJSONFromUrl(anyString(), anyString(), anyString())).thenReturn(null);
    LambdaTestRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);
    lambdaTestProxy = LambdaTestRemoteProxy.getNewInstance(request, registry);

    // We add both nodes to the registry
    registry.add(lambdaTestProxy);

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
    ObjectName objectName = new ObjectName(
        "org.seleniumhq.grid:type=RemoteProxy,node=\"https://hub.lambdatest.com\"");
    new JMXHelper().unregister(objectName);
    objectName = new ObjectName("org.seleniumhq.grid:type=RemoteProxy,node=\"http://localhost:30000\"");
    new JMXHelper().unregister(objectName);
    LambdaTestRemoteProxy.restoreCommonProxyUtilities();
    LambdaTestRemoteProxy.restoreGa();
    LambdaTestRemoteProxy.restoreEnvironment();
  }

  @Test
  public void checkProxyOrdering() {
    // Checking that the DockerSeleniumStarterProxy should come before
    // LambdaTestRemoteProxy
    List<RemoteProxy> sorted = registry.getAllProxies().getSorted();
    Assert.assertEquals(2, sorted.size());
    Assert.assertEquals(DockerSeleniumRemoteProxy.class, sorted.get(0).getClass());
    Assert.assertEquals(LambdaTestRemoteProxy.class, sorted.get(1).getClass());
  }

  @Test
  public void sessionIsCreatedWithCapabilitiesThatDockerSeleniumCannotProcess() {
    // Capability which should result in a created session
    Map<String, Object> requestedCapability = new HashMap<>();
    requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.SAFARI);
    requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.MAC);

    Assert.assertEquals(0, lambdaTestProxy.getNumberOfSessions());
    TestSession testSession = lambdaTestProxy.getNewSession(requestedCapability);

    Assert.assertNotNull(testSession);
    Assert.assertEquals(1, lambdaTestProxy.getNumberOfSessions());
  }

  @Test
  public void credentialsAreAddedInSessionCreation() throws IOException {
    // Capability which should result in a created session
    Map<String, Object> requestedCapability = new HashMap<>();
    requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
    requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN8);

    // Getting a test session in the LambdaTest node
    TestSession testSession = lambdaTestProxy.getNewSession(requestedCapability);
    Assert.assertNotNull(testSession);

    // We need to mock all the needed objects to forward the session and see how in
    // the beforeMethod
    // the LambdaTest user and accessKey get added to the body request.
    WebDriverRequest request = TestUtils.getMockedWebDriverRequestStartSession(BrowserType.IE, Platform.WIN8);

    HttpServletResponse response = mock(HttpServletResponse.class);
    ServletOutputStream stream = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(stream);

    testSession.setExternalKey(new ExternalSessionKey("LambdaTest Test"));
    testSession.forward(request, response, true);

    Environment env = new Environment();
    // The body should now have the LambdaTest variables
    String expectedBody = String.format(
        "{\"desiredCapabilities\":{\"browserName\":\"internet explorer\",\"platformName\":"
            + "\"WIN8\",\"user\":\"%s\",\"accessKey\":\"%s\",\"version\":\"latest\"}}",
        env.getStringEnvVariable("LT_USERNAME", ""), env.getStringEnvVariable("LT_ACCESS_KEY", ""));
    verify(request).setBody(expectedBody);
  }

  @Test
  public void testInformationIsRetrievedWhenStoppingSession() throws IOException {
    // Capability which should result in a created session
    try {
      Map<String, Object> requestedCapability = new HashMap<>();
      requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
      requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN10);

      // Getting a test session in the LambdaTest node
      LambdaTestRemoteProxy lambdaTestSpyProxy = spy(lambdaTestProxy);
      JsonElement informationSample = TestUtils.getTestInformationSample("lambdatest_testinformation.json");
      CommonProxyUtilities commonProxyUtilities = mock(CommonProxyUtilities.class);
      when(commonProxyUtilities.readJSONFromUrl(anyString(), anyString(), anyString())).thenReturn(informationSample);
      LambdaTestRemoteProxy.setCommonProxyUtilities(commonProxyUtilities);

      TestSession testSession = lambdaTestSpyProxy.getNewSession(requestedCapability);
      Assert.assertNotNull(testSession);
      String mockSeleniumSessionId = "3cab2e5a97aca25daadb4c3afa79c0aa";
      testSession.setExternalKey(new ExternalSessionKey(mockSeleniumSessionId));

      // We release the session, the node should be free
      WebDriverRequest request = mock(WebDriverRequest.class);
      HttpServletResponse response = mock(HttpServletResponse.class);
      when(request.getMethod()).thenReturn("DELETE");
      when(request.getRequestType()).thenReturn(RequestType.STOP_SESSION);
      testSession.getSlot().doFinishRelease();
      lambdaTestSpyProxy.afterCommand(testSession, request, response);

      verify(lambdaTestSpyProxy, timeout(1000 * 5)).getTestInformation(mockSeleniumSessionId);
      TestInformation testInformation = lambdaTestSpyProxy.getTestInformation(mockSeleniumSessionId);
      Assert.assertEquals("loadZalandoPageAndCheckTitle", testInformation.getTestName());
      Assert.assertThat(testInformation.getFileName(),
          CoreMatchers.containsString("lambdatest_loadZalandoPageAndCheckTitle_chrome_windows"));
      Assert.assertEquals("chrome 67.0, windows", testInformation.getBrowserAndPlatform());
      Assert.assertEquals("https://d15x9hjibri3lt.cloudfront.net/ZVRUI-72B5S-OKGNO-1VXVS/video.mp4",
          testInformation.getVideoUrl());
    } finally {
      LambdaTestRemoteProxy.restoreCommonProxyUtilities();
    }
  }

  @Test
  public void requestIsNotModifiedInOtherRequestTypes() throws IOException {
    // Capability which should result in a created session
    Map<String, Object> requestedCapability = new HashMap<>();
    requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
    requestedCapability.put(CapabilityType.PLATFORM_NAME, Platform.WIN8);

    // Getting a test session in the LambdaTest node
    TestSession testSession = lambdaTestProxy.getNewSession(requestedCapability);
    Assert.assertNotNull(testSession);

    // We need to mock all the needed objects to forward the session and see how in
    // the beforeMethod
    // the LambdaTest user and accessKey get added to the body request.
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

    testSession.setExternalKey(new ExternalSessionKey("LambdaTest Test"));
    testSession.forward(request, response, true);

    Assert.assertThat(request.getBody(), CoreMatchers.containsString(jsonObject.toString()));
    Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("user")));
    Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("accessKey")));

    when(request.getMethod()).thenReturn("GET");

    testSession.forward(request, response, true);
    Assert.assertThat(request.getBody(), CoreMatchers.containsString(jsonObject.toString()));
    Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("user")));
    Assert.assertThat(request.getBody(), CoreMatchers.not(CoreMatchers.containsString("accessKey")));
  }

  @Test
  public void checkVideoFileExtensionAndProxyName() {
    Assert.assertEquals(".mp4", lambdaTestProxy.getVideoFileExtension());
    Assert.assertEquals("LambdaTest", lambdaTestProxy.getProxyName());
  }
}
