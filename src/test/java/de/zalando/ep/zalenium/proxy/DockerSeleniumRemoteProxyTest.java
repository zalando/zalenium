package de.zalando.ep.zalenium.proxy;

import com.spotify.docker.client.exceptions.DockerException;
import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerFactory;
import de.zalando.ep.zalenium.util.Environment;
import de.zalando.ep.zalenium.util.TestUtils;
import org.awaitility.Duration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.web.servlet.handler.RequestType;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.BrowserType;
import org.openqa.selenium.remote.CapabilityType;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(value = Parameterized.class)
public class DockerSeleniumRemoteProxyTest {

    private DockerSeleniumRemoteProxy proxy;
    private Registry registry;
    private ContainerClient containerClient;
    private Supplier<ContainerClient> originalDockerContainerClient;

    public DockerSeleniumRemoteProxyTest(ContainerClient containerClient) {
        this.containerClient = containerClient;
        
        this.originalDockerContainerClient = ContainerFactory.getDockerContainerClientGenerator();
        // Change the factory to return our version of the Container Client
        ContainerFactory.setDockerContainerClientGenerator(() -> containerClient);
    }

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {TestUtils.getMockedDockerContainerClient()}
        });
    }

    @Before
    public void setUp() throws DockerException, InterruptedException, IOException {
        registry = Registry.newInstance();

        // Creating the configuration and the registration request of the proxy (node)
        RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(40000,
                DockerSeleniumRemoteProxy.class.getCanonicalName());
        request.getConfiguration().capabilities.clear();
        request.getConfiguration().capabilities.addAll(TestUtils.getDockerSeleniumCapabilitiesForTesting());

        // Creating the proxy
        proxy = DockerSeleniumRemoteProxy.getNewInstance(request, registry);

        proxy.setContainerClient(containerClient);
    }

    @After
    public void tearDown() {
        ContainerFactory.setDockerContainerClientGenerator(originalDockerContainerClient);
        proxy.restoreContainerClient();
    }

    @Test
    public void dockerSeleniumOnlyRunsOneTestPerContainer() {
        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        // Not tests have been executed.
        Assert.assertEquals(0, proxy.getAmountOfExecutedTests());

        TestSession newSession = proxy.getNewSession(requestedCapability);

        Assert.assertNotNull(newSession);

        // One test is/has been executed and the session amount limit was reached.
        Assert.assertEquals(1, proxy.getAmountOfExecutedTests());
        Assert.assertTrue(proxy.isTestSessionLimitReached());
    }

    @Test
    public void secondRequestGetsANullTestRequest() {
        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("name", "anyRandomTestName");
        requestedCapability.put("group", "anyRandomTestGroup");

        {
            TestSession newSession = proxy.getNewSession(requestedCapability);
            Assert.assertNotNull(newSession);
        }

        // Since only one test should be executed, the second request should come null
        {
            TestSession newSession = proxy.getNewSession(requestedCapability);
            Assert.assertNull(newSession);
        }

        Assert.assertEquals("anyRandomTestGroup", proxy.getTestGroup());
        Assert.assertEquals("anyRandomTestName", proxy.getTestName());
    }

    @Test
    public void noSessionIsCreatedWhenCapabilitiesAreNotSupported() {
        // Non supported capabilities
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.IE);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.WIN10);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNull(newSession);
    }

    @Test
    public void testIdleTimeoutUsesDefaultValueWhenCapabilityIsNotPresent() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), DockerSeleniumRemoteProxy.DEFAULT_MAX_TEST_IDLE_TIME_SECS);
    }

    @Test
    public void testIdleTimeoutUsesDefaultValueWhenCapabilityHasNegativeValue() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("idleTimeout", -20L);

        proxy.getNewSession(requestedCapability);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), DockerSeleniumRemoteProxy.DEFAULT_MAX_TEST_IDLE_TIME_SECS);
    }

    @Test
    public void testIdleTimeoutUsesDefaultValueWhenCapabilityHasFaultyValue() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("idleTimeout", "thisValueIsNAN Should not work.");

        proxy.getNewSession(requestedCapability);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), DockerSeleniumRemoteProxy.DEFAULT_MAX_TEST_IDLE_TIME_SECS);
    }

    @Test
    public void testIdleTimeoutUsesValueInStringPassedAsCapability() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("idleTimeout", "200");

        proxy.getNewSession(requestedCapability);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), 200L);
    }

    @Test
    public void testIdleTimeoutUsesValuePassedAsCapability() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("idleTimeout", 180L);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);
        Assert.assertEquals(proxy.getMaxTestIdleTimeSecs(), 180L);
    }

    @Test
    public void pollerThreadTearsDownNodeAfterTestIsCompleted() throws IOException {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        // Start poller thread
        proxy.startPolling();

        // Get a test session
        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        // The node should be busy since there is a session in it
        Assert.assertTrue(proxy.isBusy());

        // We release the session, the node should be free
        WebDriverRequest request = mock(WebDriverRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestType()).thenReturn(RequestType.STOP_SESSION);

        newSession.getSlot().doFinishRelease();
        proxy.afterCommand(newSession, request, response);
        proxy.afterSession(newSession);

        // After running one test, the node shouldn't be busy and also down
        Assert.assertFalse(proxy.isBusy());
        Callable<Boolean> callable = () -> proxy.isDown();
        await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(callable);
    }

    @Test
    public void normalSessionCommandsDoNotStopNode() throws IOException {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();

        // Start poller thread
        proxy.startPolling();

        // Get a test session
        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        // The node should be busy since there is a session in it
        Assert.assertTrue(proxy.isBusy());

        // We release the session, the node should be free
        WebDriverRequest request = mock(WebDriverRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestType()).thenReturn(RequestType.REGULAR);

        proxy.afterCommand(newSession, request, response);

        // The node should not tear down
        Assert.assertTrue(proxy.isBusy());
        Callable<Boolean> callable = () -> !proxy.isDown();
        await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.TWO_SECONDS).until(callable);
    }

    @Test
    public void nodeShutsDownWhenTestIsIdle() throws IOException {

        // Supported desired capability for the test session
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("idleTimeout", 1L);

        DockerSeleniumRemoteProxy spyProxy = spy(proxy);

        // Start poller thread
        spyProxy.startPolling();

        // Get a test session
        TestSession newSession = spyProxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);

        // Start the session
        WebDriverRequest webDriverRequest = mock(WebDriverRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(webDriverRequest.getMethod()).thenReturn("POST");
        when(webDriverRequest.getRequestType()).thenReturn(RequestType.START_SESSION);
        spyProxy.beforeCommand(newSession, webDriverRequest, response);

        // The node should be busy since there is a session in it
        Assert.assertTrue(spyProxy.isBusy());

        // The node should tear down after the maximum idle time is elapsed
        Assert.assertTrue(spyProxy.isBusy());
        Callable<Boolean> callable = spyProxy::isDown;
        await().pollInterval(Duration.FIVE_HUNDRED_MILLISECONDS).atMost(Duration.FIVE_SECONDS).until(callable);
    }

    @Test
    public void fallbackToDefaultValueWhenEnvVariableIsNotABoolean() {
        try {
            Environment environment = mock(Environment.class, withSettings().useConstructor());
            when(environment.getEnvVariable(DockerSeleniumRemoteProxy.ZALENIUM_VIDEO_RECORDING_ENABLED))
                    .thenReturn("any_nonsense_value");
            when(environment.getBooleanEnvVariable(any(String.class), any(Boolean.class))).thenCallRealMethod();
            DockerSeleniumRemoteProxy.setEnv(environment);
            DockerSeleniumRemoteProxy.readEnvVarForVideoRecording();

            Assert.assertEquals(DockerSeleniumRemoteProxy.DEFAULT_VIDEO_RECORDING_ENABLED,
                    DockerSeleniumRemoteProxy.isVideoRecordingEnabled());
        } finally {
            DockerSeleniumRemoteProxy.restoreEnvironment();
        }
    }

    @Test
    public void videoRecordingIsStartedAndStopped() throws DockerException, InterruptedException,
            URISyntaxException, IOException {

            // Create a docker-selenium container
            RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30000,
                    DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
            DockerSeleniumStarterRemoteProxy dsProxy = new DockerSeleniumStarterRemoteProxy(request, registry);
            DockerSeleniumStarterRemoteProxy.setMaxDockerSeleniumContainers(1);
            DockerSeleniumStarterRemoteProxy.setConfiguredScreenSize(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE);
            DockerSeleniumStarterRemoteProxy.setContainerClient(containerClient);
            dsProxy.getNewSession(getCapabilitySupportedByDockerSelenium());

            // Creating a spy proxy to verify the invoked methods
            DockerSeleniumRemoteProxy spyProxy = spy(proxy);

            // Start poller thread
            spyProxy.startPolling();

            // Get a test session
            TestSession newSession = spyProxy.getNewSession(getCapabilitySupportedByDockerSelenium());
            Assert.assertNotNull(newSession);

            // We start the session, in order to start recording
            WebDriverRequest webDriverRequest = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("POST");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.START_SESSION);
            spyProxy.afterCommand(newSession, webDriverRequest, response);

            // Assert video recording started
            String containerId = spyProxy.getContainerId();
            verify(spyProxy, times(1)).
                    videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING);
            verify(spyProxy, times(1)).
                    processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING,
                            containerId);

            // We release the sessions, the node should be free
            webDriverRequest = mock(WebDriverRequest.class);
            response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("DELETE");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.STOP_SESSION);

            newSession.getSlot().doFinishRelease();
            spyProxy.afterCommand(newSession, webDriverRequest, response);
            spyProxy.afterSession(newSession);

            Assert.assertFalse(spyProxy.isBusy());
            verify(spyProxy, timeout(40000))
                    .videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING);
            verify(spyProxy, timeout(40000))
                    .processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING,
                            containerId);
            verify(spyProxy, timeout(40000)).copyVideos(containerId);
    }

    @Test
    public void videoRecordingIsDisabled() throws DockerException, InterruptedException, IOException, URISyntaxException {

        try {
            // Create a docker-selenium container
            RegistrationRequest request = TestUtils.getRegistrationRequestForTesting(30000,
                    DockerSeleniumStarterRemoteProxy.class.getCanonicalName());
            DockerSeleniumStarterRemoteProxy dsProxy = new DockerSeleniumStarterRemoteProxy(request, registry);
            DockerSeleniumStarterRemoteProxy.setMaxDockerSeleniumContainers(1);
            DockerSeleniumStarterRemoteProxy.setConfiguredScreenSize(DockerSeleniumStarterRemoteProxy.DEFAULT_SCREEN_SIZE);
            DockerSeleniumStarterRemoteProxy.setConfiguredTimeZone(DockerSeleniumStarterRemoteProxy.DEFAULT_TZ.getID());
            DockerSeleniumStarterRemoteProxy.setContainerClient(containerClient);
            dsProxy.getNewSession(getCapabilitySupportedByDockerSelenium());

            // Mocking the environment variable to return false for video recording enabled
            Environment environment = mock(Environment.class);
            when(environment.getEnvVariable(DockerSeleniumRemoteProxy.ZALENIUM_VIDEO_RECORDING_ENABLED))
                    .thenReturn("false");

            // Creating a spy proxy to verify the invoked methods
            DockerSeleniumRemoteProxy spyProxy = spy(proxy);
            DockerSeleniumRemoteProxy.setEnv(environment);
            DockerSeleniumRemoteProxy.readEnvVarForVideoRecording();

            // Start poller thread
            spyProxy.startPolling();

            // Get a test session
            TestSession newSession = spyProxy.getNewSession(getCapabilitySupportedByDockerSelenium());
            Assert.assertNotNull(newSession);

            // We start the session, in order to start recording
            WebDriverRequest webDriverRequest = mock(WebDriverRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("POST");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.START_SESSION);
            spyProxy.afterCommand(newSession, webDriverRequest, response);

            // Assert no video recording was started, videoRecording is invoked but processContainerAction should not
            verify(spyProxy, times(1))
                    .videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING);
            verify(spyProxy, never())
                    .processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.START_RECORDING, "");

            // We release the sessions, the node should be free
            webDriverRequest = mock(WebDriverRequest.class);
            response = mock(HttpServletResponse.class);
            when(webDriverRequest.getMethod()).thenReturn("DELETE");
            when(webDriverRequest.getRequestType()).thenReturn(RequestType.STOP_SESSION);

            newSession.getSlot().doFinishRelease();
            spyProxy.afterCommand(newSession, webDriverRequest, response);
            spyProxy.afterSession(newSession);

            Assert.assertFalse(spyProxy.isBusy());
            // Now we assert that videoRecording was invoked but processContainerAction not, neither copyVideos
            verify(spyProxy, timeout(40000))
                    .videoRecording(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING);
            verify(spyProxy, never())
                    .processContainerAction(DockerSeleniumRemoteProxy.DockerSeleniumContainerAction.STOP_RECORDING, "");
            verify(spyProxy, never()).copyVideos("");
        } finally {
            DockerSeleniumRemoteProxy.restoreEnvironment();
        }
    }

    @Test
    public void videoRecordingIsDisabledViaCapability() {
        Map<String, Object> requestedCapability = getCapabilitySupportedByDockerSelenium();
        requestedCapability.put("recordVideo", false);

        TestSession newSession = proxy.getNewSession(requestedCapability);
        Assert.assertNotNull(newSession);
        Assert.assertEquals(DockerSeleniumRemoteProxy.isVideoRecordingEnabled(), false);
    }

    private Map<String, Object> getCapabilitySupportedByDockerSelenium() {
        Map<String, Object> requestedCapability = new HashMap<>();
        requestedCapability.put(CapabilityType.BROWSER_NAME, BrowserType.CHROME);
        requestedCapability.put(CapabilityType.PLATFORM, Platform.LINUX);
        return requestedCapability;
    }
}
